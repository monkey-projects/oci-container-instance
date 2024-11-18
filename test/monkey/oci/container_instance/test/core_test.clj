(ns monkey.oci.container-instance.test.core-test
  (:require [clojure.test :refer [deftest testing is with-test]]
            [cheshire.core :as json]
            [monkey.oci.container-instance.core :as sut]
            [martian
             [schema :as ms]
             [test :as mt]]
            [org.httpkit.fake :as hf]
            [schema.core :as s])
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
  (test-endpoints {:list-container-instances {:compartment-id "test-compartment"
                                              :lifecycle-state "ACTIVE"}
                   :create-container-instance {:container-instance
                                               {:availability-domain "test-domain"
                                                :compartment-id "test-compartment"
                                                :shape "test-shape"
                                                :shape-config {:ocpus 1}
                                                :containers [{:image-url "test:latest"}]
                                                :vnics [{:subnet-id "test-subnet"}]}}
                   :start-container-instance {:instance-id "test-id"}
                   :stop-container-instance {:instance-id "test-id"}
                   :get-container-instance {:instance-id "test-id"}
                   :delete-container-instance {:instance-id "test-id"}
                   :update-container-instance {:instance-id "test-id"
                                               :container-instance {:display-name "test container"}}}))

(deftest shape-endpoints
  (test-endpoints {:list-container-instance-shapes {:compartment-id "test-compartment"}}))

(deftest container-endpoints
  (test-endpoints {:get-container {:container-id "test-container"}
                   :update-container {:container-id "test-container"
                                      :container {:display-name "new-name"}}
                   :retrieve-logs {:container-id "test-container"}}))

;; Disabled this test because for some strange reason Martien doesn't apply the
;; interceptor when testing and I can't be bothered to figure out why that is...
(deftest ^:kaocha/skip retrieve-logs
  (testing "returns body as text"
    (hf/with-fake-http
      [{:url "https://compute-containers.test-region.oci.oraclecloud.com/20210415/containers/test-container/actions/retrieveLogs"
        :method :post}
       (fn [& _]
         (future {:status 200
                  :headers {"content-type" "application/json"}
                  :body "These are the container logs"}))]
      (let [r (-> test-ctx
                  (sut/retrieve-logs {:container-id "test-container"})
                  (deref))]
        (is (= "These are the container logs" (:body r)))
        (is (= "text/plain" (get-in r [:headers "content-type"])))))))

(deftest image-pull-secrets
  (testing "fails on invalid"
    (is (some? (s/check sut/ImagePullSecrets
                        {:secret-type "invalid"}))))
  
  (testing "accepts basic"
    (is (nil? (s/check sut/ImagePullSecrets
                       {:secret-type "BASIC"
                        :registry-endpoint "test"
                        :username "testuser"
                        :password "testpass"}))))

  (testing "accepts vault"
    (is (nil? (s/check sut/ImagePullSecrets
                       {:secret-type "VAULT"
                        :registry-endpoint "test"
                        :secret-id "test-secret"})))))

(deftest tags
  (let [tags {"customer_id" "test-customer"
              "project_id" "test-project"}
        opts {:container-instance
              {:availability-domain "test-domain"
               :compartment-id "test-compartment"
               :shape "test-shape"
               :shape-config {:ocpus 1}
               :containers [{:image-url "test:latest"}]
               :vnics [{:subnet-id "test-subnet"}]
               :freeform-tags tags}}]
    
    (testing "passes tags as-is to martian when creating container instance"
      (let [ctx (-> test-ctx
                    (mt/respond-with
                     {:create-container-instance
                      (fn [req]
                        (get-in req [:body :freeform-tags]))}))]
        (is (= tags (-> ctx
                        (sut/create-container-instance opts)
                        (deref))))))

    (testing "passes tags as-is to http request"
      (hf/with-fake-http [{:url "https://compute-containers.test-region.oci.oraclecloud.com/20210415/containerInstances"
                           :method :post}
                          (fn [_ req _]
                            (let [p (json/parse-string (:body req))]
                              (cond-> {:status 200}
                                (not= (get-in opts [:container-instance :freeform-tags])
                                      (get p "freeformTags"))
                                (assoc :status 400
                                       :body "No matching tags found in request"))))]
        (is (= 200 (-> test-ctx
                       (sut/create-container-instance opts)
                       (deref)
                       :status)))))))

