(ns monkey.oci.container-instance.core
  (:require [martian.core :as martian]
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

(def health-check-base
  {(s/optional-key :failureAction) (s/constrained s/Str #{"KILL" "NONE"})
   (s/optional-key :failureThreshold) (s/constrained s/Int pos?)
   :healthCheckType (s/constrained s/Str #{"HTTP" "TCP" "COMMAND"})
   (s/optional-key :initialDelayInSeconds) s/Int
   (s/optional-key :intervalInSeconds) (s/constrained s/Int pos?)
   (s/optional-key :name) s/Str
   (s/optional-key :successThreshold) (s/constrained s/Int pos?)
   (s/optional-key :timeoutInSeconds) (s/constrained s/Int pos?)})

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
  (s/conditional (prop-matches? :healthCheckType "COMMAND") CommandHealthCheck
                 (prop-matches? :healthCheckType "HTTP") HttpHealthCheck
                 (prop-matches? :healthCheckType "TCP") TcpHealthCheck))

(s/defschema ResourceConfig
  {(s/optional-key :memoryLimitInGBs) s/Int
   (s/optional-key :vcpusLimit) s/Int})

(s/defschema SecurityContext
  {(s/optional-key :securityContextType) (s/constrained s/Str #{"LINUX"})})

(s/defschema VolumeMount
  {:volumeName s/Str
   :mountPath s/Str
   (s/optional-key :isReadOnly) s/Bool
   (s/optional-key :partition) (s/constrained s/Int pos?)
   (s/optional-key :subPath) s/Str})

(s/defschema ContainerDetails
  {(s/optional-key :arguments) [s/Str]
   (s/optional-key :command) [s/Str]
   (s/optional-key :displayName) s/Str
   (s/optional-key :environmentVariables) {s/Str s/Str}
   (s/optional-key :healthChecks) [HealthCheck]
   :imageUrl s/Str
   (s/optional-key :isResourcePrincipalDisabled) s/Bool
   (s/optional-key :resourceConfig) ResourceConfig
   (s/optional-key :securityContext) SecurityContext
   (s/optional-key :volumeMounts) [VolumeMount]
   (s/optional-key :workingDirectory) s/Str})

(s/defschema DnsConfig
  {(s/optional-key :nameservers) (s/constrained [s/Str] (max-count? 3))
   (s/optional-key :options) (s/constrained [s/Str] (max-count? 16))
   (s/optional-key :searches) (s/constrained [s/Str] (max-count? 6))})

(s/defschema ShapeConfig
  {(s/optional-key :memoryInGBs) s/Int
   :ocpus s/Int})

(def image-pull-secrets-base
  {:secretType (s/constrained s/Str #{"BASIC" "VAULT"})
   :registryEndpoint s/Str})

(s/defschema BasicImagePullSecrets
  (assoc image-pull-secrets-base
         :username s/Str
         :password s/Str))

(s/defschema VaultImagePullSecrets
  (assoc image-pull-secrets-base
         :secretId s/Str))

(s/defschema ImagePullSecrets
  (s/conditional (prop-matches? :secretType "BASIC") BasicImagePullSecrets
                 (prop-matches? :secretType "VAULT") VaultImagePullSecrets))

(s/defschema VnicDetails
  {(s/optional-key :displayName) s/Str
   (s/optional-key :hostnameLabel) s/Str
   (s/optional-key :isPublicIpAssigned) s/Bool
   (s/optional-key :nsgIds) [s/Str]
   (s/optional-key :privateIp) s/Str
   (s/optional-key :skipSourceDestCheck) s/Bool
   :subnetId s/Str})

(def volume-details-base
  {:name s/Str
   :volumeType (s/constrained s/Str #{"EMPTYDIR" "CONFIGFILE"})})

(s/defschema EmptyDirVolumeDetails
  (assoc volume-details-base
         :backingStore s/Str))

(s/defschema ConfigFile
  {:data s/Str
   :fileName s/Str
   (s/optional-key :path) s/Str})

(s/defschema ConfigFileVolumeDetails
  (assoc volume-details-base
         :configs [ConfigFile]))

(s/defschema VolumeDetails
  (s/conditional (prop-matches? :volumeType "EMPTYDIR") EmptyDirVolumeDetails
                 (prop-matches? :volumeType "CONFIGFILE") ConfigFileVolumeDetails))

(s/defschema ContainerInstanceDetails
  {:availabilityDomain s/Str
   :compartmentId s/Str
   (s/optional-key :containerRestartPolicy) (s/constrained s/Str #{"ALWAYS" "NEVER"})
   :containers [ContainerDetails]
   (s/optional-key :displayName) s/Str
   (s/optional-key :dnsConfig) DnsConfig
   (s/optional-key :faultDomain) s/Str
   (s/optional-key :gracefulShutdownTimeoutInSeconds) s/Int
   (s/optional-key :imagePullSecrets) ImagePullSecrets
   :shape s/Str
   :shapeConfig ShapeConfig
   ;; FIXME Martian fails to convert they keys when there is a condition
   :vnics [VnicDetails] #_(s/constrained [VnicDetails] (comp (partial = 1) count))
   (s/optional-key :volumes) (s/constrained [VolumeDetails] (max-count? 32))})

(def routes
  [(p/paged-route
    {:route-name :list-container-instances
     :method :get
     :path-parts ["/containerInstances"]
     :query-schema {:compartmentId s/Str}
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
    :path-parts ["/containerInstances/:instance-id/actions/start"]
    :path-schema {:instance-id s/Str}}

   {:route-name :stop-container-instance
    :method :post
    :path-parts ["/containerInstances/:instance-id/actions/stop"]
    :path-schema {:instance-id s/Str}}

   {:route-name :get-container-instance
    :method :get
    :path-parts ["/containerInstances/:instance-id"]
    :path-schema {:instance-id s/Str}}
   
   {:route-name :delete-container-instance
    :method :delete
    :path-parts ["/containerInstances/:instance-id"]
    :path-schema {:instance-id s/Str}}])

(def host (comp (partial format "https://compute-containers.%s.oci.oraclecloud.com/20210415") :region))

(defn make-context
  "Creates Martian context for the given configuration.  This context
   should be passed to subsequent requests."
  [conf]
  (cm/make-context conf host routes))

(def send-request martian/response-for)

(u/define-endpoints *ns* routes send-request)
