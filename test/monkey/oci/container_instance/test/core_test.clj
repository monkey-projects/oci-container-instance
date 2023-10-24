(ns monkey.oci.container-instance.test.core-test
  (:require [clojure.test :refer [deftest testing is with-test]]
            [monkey.oci.container-instance.core :as sut]
            [martian.test :as mt])
  (:import java.security.KeyPairGenerator))

(defn generate-key []
  (-> (doto (KeyPairGenerator/getInstance "RSA")
        (.initialize 2048))
      (.generateKeyPair)
      (.getPrivate)))

(def test-config {:user-ocid "test-user"
                  :tenancy-ocid "test-tenancy"
                  :private-key (generate-key)
                  :key-fingerprint "test-fingerprint"
                  :region "test-region"})

(def test-ctx (sut/make-context test-config))

(defn- test-call
  ([ctx f route opts]
   (let [r (-> ctx
               (mt/respond-with-constant {route {:body "[]" :status 200}})
               (f opts)
               (deref))]
     (is (map? r))
     (is (= 200 (:status r)))))
  ([ctx route opts]
   (let [f (some->> route symbol (ns-resolve 'monkey.oci.container-instance.core) var-get)]
     (is (fn? f) (str "no binding found for " route))
     (when f
       (test-call ctx f route opts)))))

;; Since all tests are more or less the same, let's use a seq instead of copy/pasting.

(defn test-endpoints
  ([ctx ep]
   (doseq [[k v] ep]
     (testing (format "invokes `%s` endpoint" k)
       (test-call ctx k v))))
  ([ep]
   (test-endpoints test-ctx ep)))

(deftest container-instance-endpoints
  (test-endpoints {:list-container-instances {:compartment-id "test-compartment"}}))

(deftest shape-endpoints
  (test-endpoints {:list-container-instance-shapes {:compartment-id "test-compartment"}}))
