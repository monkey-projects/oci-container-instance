(ns monkey.oci.container-instance.core
  (:require [martian.core :as martian]
            [monkey.oci.common
             [martian :as cm]
             [pagination :as p]
             [utils :as u]]
            [schema.core :as s]))

(def json #{"application/json"})

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
     :produces json})])

(def host (comp (partial format "https://compute-containers.%s.oci.oraclecloud.com/20210415") :region))

(defn make-context
  "Creates Martian context for the given configuration.  This context
   should be passed to subsequent requests."
  [conf]
  (cm/make-context conf host routes))

(def send-request martian/response-for)

(u/define-endpoints *ns* routes send-request)
