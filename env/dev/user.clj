(ns user
  (:require [camel-snake-kebab.core :as csk]
            [clojure.core.async :as ca]
            [config.core :refer [env]]
            [manifold.deferred :as md]
            [monkey.oci.common.utils :as u]
            [monkey.oci.container-instance.core :as c]))

(def conf (-> env
              (select-keys [:user-ocid :tenancy-ocid :key-fingerprint :private-key :region])
              (update :private-key u/load-privkey)))

(def cid (:compartment-ocid env))

(def ctx (c/make-context conf))

(defn create-test-instance []
  (c/create-container-instance
   ctx
   {:container-instance
    {:compartment-id cid
     :availability-domain "GARu:EU-FRANKFURT-1-AD-1"
     :shape "CI.Standard.A1.Flex" ; Use ARM shape, it's cheaper
     :shape-config {:ocpus 1
                    :memory-in-g-bs 1}
     :display-name "test-instance"
     :freeform-tags {"env" "test"}
     :vnics [{:subnet-id "ocid1.subnet.oc1.eu-frankfurt-1.aaaaaaaaeq6pmdp5teajste66ewnuiqatng7r6ffmn2432l3ttuefgftl6gq"}]
     :containers
     [{:image-url "docker.io/httpd:2.4"
       :display-name "apache"
       :environment-variables {"TEST_VAR" "test value"}}]}}))

(defn list-instances []
  (md/chain
   (c/list-container-instances ctx {:compartment-id cid})
   :body
   :items))

(defn delete-all-instances
  "Deletes all instances in the compartment.  Be careful with this..."
  []
  (md/chain
   (list-instances)
   (partial remove (comp (partial = "DELETED") :lifecycle-state))
   (partial map :id)
   (partial map #(c/delete-container-instance ctx {:instance-id %}))
   (partial apply md/zip)))
