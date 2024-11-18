(ns monkey.oci.container-instance.core
  (:require [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [martian
             [core :as martian]
             [interceptors :as mi]]
            [monkey.oci.common
             [martian :as cm]
             [pagination :as p]
             [utils :as u]]
            [schema.core :as s]))

(def json #{"application/json"})

(defn- prop-matches? [k v]
  (comp (partial = v) k))

(defn- max-count? [n]
  (comp (partial >= n) count))

(defn- between?
  "Checks if the value is between the two values (inclusive)"
  [lo hi]
  #(<= lo % hi))

(def tag-map {s/Str s/Str})

(s/defschema FreeformTags tag-map)
(s/defschema DefinedTags {s/Str tag-map})

(defn- with-tags
  "Adds tags properties to schema"
  [s]
  (assoc s
         (s/optional-key :freeform-tags) FreeformTags
         (s/optional-key :defined-tags) DefinedTags))

(def health-check-base
  {(s/optional-key :failure-action) (s/constrained s/Str #{"KILL" "NONE"})
   (s/optional-key :failure-threshold) (s/constrained s/Int pos?)
   :health-check-type (s/constrained s/Str #{"HTTP" "TCP" "COMMAND"})
   (s/optional-key :initial-delay-in-seconds) s/Int
   (s/optional-key :interval-in-seconds) (s/constrained s/Int pos?)
   (s/optional-key :name) s/Str
   (s/optional-key :success-threshold) (s/constrained s/Int pos?)
   (s/optional-key :timeout-in-seconds) (s/constrained s/Int pos?)})

(s/defschema CommandHealthCheck
  (assoc health-check-base
         :command [s/Str]))

(s/defschema HttpHealthCheckHeader
  {:name s/Str
   :value s/Str})

(s/defschema HttpHealthCheck
  (assoc health-check-base
         (s/optional-key :headers) [HttpHealthCheckHeader]
         :path s/Str
         :port s/Int))

(s/defschema TcpHealthCheck
  (assoc health-check-base
         :port s/Int))

(s/defschema HealthCheck
  (s/conditional (prop-matches? :health-check-type "COMMAND") CommandHealthCheck
                 (prop-matches? :health-check-type "HTTP") HttpHealthCheck
                 (prop-matches? :health-check-type "TCP") TcpHealthCheck))

(s/defschema ResourceConfig
  {(s/optional-key :memory-limit-in-g-bs) s/Int
   (s/optional-key :vcpus-limit) s/Int})

(def security-context-base
  {(s/optional-key :security-context-type) (s/constrained s/Str #{"LINUX"})})

(s/defschema LinuxSecurityContext
  (assoc security-context-base
         (s/optional-key :is-non-root-user-check-enabled) s/Bool
         (s/optional-key :is-root-file-system-readonly) s/Bool
         (s/optional-key :run-as-group) (s/constrained s/Int (between? 0 65535))
         (s/optional-key :run-as-user) (s/constrained s/Int (between? 0 65535))))

(s/defschema SecurityContext
  (s/conditional (prop-matches? :security-context-type "LINUX") LinuxSecurityContext))

(s/defschema VolumeMount
  {:volume-name s/Str
   :mount-path s/Str
   (s/optional-key :is-read-only) s/Bool
   (s/optional-key :partition) (s/constrained s/Int pos?)
   (s/optional-key :sub-path) s/Str})

(s/defschema ContainerDetails
  (with-tags
    {(s/optional-key :arguments) [s/Str]
     (s/optional-key :command) [s/Str]
     (s/optional-key :display-name) s/Str
     (s/optional-key :environment-variables) {s/Str s/Str}
     (s/optional-key :health-checks) [HealthCheck]
     :image-url s/Str
     (s/optional-key :is-resource-principal-disabled) s/Bool
     (s/optional-key :resource-config) ResourceConfig
     (s/optional-key :security-context) SecurityContext
     (s/optional-key :volume-mounts) [VolumeMount]
     (s/optional-key :working-directory) s/Str}))

(s/defschema DnsConfig
  {(s/optional-key :nameservers) (s/constrained [s/Str] (max-count? 3))
   (s/optional-key :options) (s/constrained [s/Str] (max-count? 16))
   (s/optional-key :searches) (s/constrained [s/Str] (max-count? 6))})

(s/defschema ShapeConfig
  {(s/optional-key :memory-in-g-bs) s/Int
   :ocpus s/Int})

(def image-pull-secrets-base
  {:secret-type (s/constrained s/Str #{"BASIC" "VAULT"})
   :registry-endpoint s/Str})

(s/defschema BasicImagePullSecrets
  (assoc image-pull-secrets-base
         :username s/Str
         :password s/Str))

(s/defschema VaultImagePullSecrets
  (assoc image-pull-secrets-base
         :secret-id s/Str))

(s/defschema ImagePullSecrets
  (s/conditional (prop-matches? :secret-type "BASIC") BasicImagePullSecrets
                 (prop-matches? :secret-type "VAULT") VaultImagePullSecrets))

(s/defschema VnicDetails
  {(s/optional-key :display-name) s/Str
   (s/optional-key :hostname-label) s/Str
   (s/optional-key :is-public-ip-assigned) s/Bool
   (s/optional-key :nsg-ids) [s/Str]
   (s/optional-key :private-ip) s/Str
   (s/optional-key :skip-source-dest-check) s/Bool
   :subnet-id s/Str})

(def volume-details-base
  {:name s/Str
   :volume-type (s/constrained s/Str #{"EMPTYDIR" "CONFIGFILE"})})

(s/defschema EmptyDirVolumeDetails
  (assoc volume-details-base
         (s/optional-key :backing-store) s/Str))

(s/defschema ConfigFile
  {:data s/Str
   :file-name s/Str
   (s/optional-key :path) s/Str})

(s/defschema ConfigFileVolumeDetails
  (assoc volume-details-base
         :configs [ConfigFile]))

(s/defschema VolumeDetails
  (s/conditional (prop-matches? :volume-type "EMPTYDIR") EmptyDirVolumeDetails
                 (prop-matches? :volume-type "CONFIGFILE") ConfigFileVolumeDetails))

(s/defschema ContainerInstanceDetails
  (with-tags 
    {:availability-domain (s/constrained s/Str not-empty)
     :compartment-id (s/constrained s/Str not-empty)
     (s/optional-key :container-restart-policy) (s/constrained s/Str #{"ALWAYS" "NEVER"})
     :containers [ContainerDetails]
     (s/optional-key :display-name) s/Str
     (s/optional-key :dns-config) DnsConfig
     (s/optional-key :fault-domain) s/Str
     (s/optional-key :graceful-shutdown-timeout-in-seconds) s/Int
     (s/optional-key :image-pull-secrets) [ImagePullSecrets]
     :shape s/Str
     :shape-config ShapeConfig
     :vnics (s/constrained [VnicDetails] (comp (partial = 1) count))
     (s/optional-key :volumes) (s/constrained [VolumeDetails] (max-count? 32))}))

(s/defschema UpdateContainerInstance
  (with-tags
    {(s/optional-key :display-name) s/Str}))

(s/defschema UpdateContainer
  (with-tags
    {(s/optional-key :display-name) s/Str}))

(s/defschema LifecycleState
  (s/constrained s/Str #{"CREATING"
                         "UPDATING"
                         "ACTIVE"
                         "INACTIVE"
                         "DELETING"
                         "DELETED"
                         "FAILED"}))

(s/defschema SortOrder
  (s/constrained s/Str #{"ASC" "DESC"}))

(s/defschema SortBy
  (s/constrained s/Str #{"timeCreated" "displayName"}))

(def instance-path ["/containerInstances/" :instance-id])

(defn instance-action-path [act]
  (into instance-path [(str "/actions/" act)]))

(def container-path ["/containers/" :container-id])

(def routes
  [(p/paged-route
    {:route-name :list-container-instances
     :method :get
     :path-parts ["/containerInstances"]
     :query-schema {:compartmentId s/Str
                    (s/optional-key :lifecycleState) LifecycleState
                    (s/optional-key :displayName) s/Str
                    (s/optional-key :availabilityDomain) s/Str
                    (s/optional-key :sortOrder) SortOrder
                    (s/optional-key :sortBy) SortBy}
     :produces json})

   (p/paged-route
    {:route-name :list-container-instance-shapes
     :method :get
     :path-parts ["/containerInstanceShapes"]
     :query-schema {:compartmentId s/Str
                    (s/optional-key :availabilityDomain) s/Str}
     :produces json})

   {:route-name :create-container-instance
    :method :post
    :path-parts ["/containerInstances"]
    :body-schema {:container-instance ContainerInstanceDetails}
    :consumes json
    :produces json}

   {:route-name :start-container-instance
    :method :post
    :path-parts (instance-action-path "start")
    :path-schema {:instance-id s/Str}}

   {:route-name :stop-container-instance
    :method :post
    :path-parts (instance-action-path "stop")
    :path-schema {:instance-id s/Str}}

   {:route-name :get-container-instance
    :method :get
    :path-parts instance-path
    :path-schema {:instance-id s/Str}
    :produces json}
   
   {:route-name :delete-container-instance
    :method :delete
    :path-parts instance-path
    :path-schema {:instance-id s/Str}}

   {:route-name :update-container-instance
    :method :put
    :path-parts instance-path
    :path-schema {:instance-id s/Str}
    :body-schema {:container-instance UpdateContainerInstance}
    :consumes json}

   {:route-name :get-container
    :method :get
    :path-parts container-path
    :path-schema {:container-id s/Str}
    :produces json}

   {:route-name :update-container
    :method :put
    :path-parts container-path
    :path-schema {:container-id s/Str}
    :body-schema {:container UpdateContainer}
    :consumes json}
   
   {:route-name :retrieve-logs
    :method :post
    :path-parts (into container-path ["/actions/retrieveLogs"])
    :path-schema {:container-id s/Str}
    :produces #{"text/plain"}}])

(def host (comp (partial format "https://compute-containers.%s.oci.oraclecloud.com/20210415") :region))

(defn- json-encode
  "Tags and environment variables should be passed as they are.  We can't just convert all
   keys in the input to camelCase because of this.  This is a fairly naive approach to only
   convert keywords, and leave strings as-is."
  [body]
  (letfn [(convert-key [k]
            (if (keyword? k)
              (csk/->camelCase (name k))
              k))]
    (when body
      (json/generate-string body {:key-fn convert-key}))))

(def encode-body (mi/encode-body {"application/json" {:encode json-encode}}))

(def retrieve-logs-fixer
  "Due to a bug in the OCI retrieveLogs endpoint, it always sets content type
   to `application/json` even though it returns plain text on a 200 response.
   This interceptor updates the content-type header in that case."
  {:name ::retrieve-logs-fixer
   :leave (fn [ctx]
            (cond-> ctx
              (= :retrieve-logs (get-in ctx [:handler :route-name]))
              (assoc-in [:response :headers :content-type] "text/plain")))})

(defn- update-interceptors [i]
  (-> i
      (mi/inject encode-body :replace ::mi/encode-body)
      ;; Don't keywordize params, it messes up our schemas.  The drawback is that
      ;; we have to be careful to pass in keywords instead strings unless explicitly
      ;; desired.
      (mi/inject nil :replace ::mi/keywordize-params)
      (mi/inject retrieve-logs-fixer :before :martian.httpkit/perform-request)))

(defn make-context
  "Creates Martian context for the given configuration.  This context
   should be passed to subsequent requests."
  [conf]
  (-> (cm/make-context conf host routes)
      (update :interceptors update-interceptors)))

(def send-request martian/response-for)

(u/define-endpoints *ns* routes send-request)
