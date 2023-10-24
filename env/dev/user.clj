(ns user
  (:require [clojure.core.async :as ca]
            [config.core :refer [env]]
            [monkey.oci.common.utils :as u]
            [monkey.oci.container-instance.core :as c]))

(def conf (-> env
              (select-keys [:user-ocid :tenancy-ocid :key-fingerprint :private-key :region])
              (update :private-key u/load-privkey)))

(def cid (:compartment-ocid env))

(def ctx (c/make-context conf))
