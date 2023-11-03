(ns container-instance.build
  (:require [monkey.ci.build
             [api :as api]
             [core :as c]]))

(defn clj [name & args]
  "Executes script in clojure container"
  {:name name
   :container/image "docker.io/clojure:temurin-20-tools-deps-alpine"
   :script [(apply str "clojure " args)]})

(def unit-test (clj "unit-test" "-X:test:junit"))
(def jar (clj "jar" "-X:jar"))

(defn deploy [ctx]
  (let [params (api/build-params ctx)]
    (assoc (clj "deploy" "-X:jar:deploy")
           :container/env (select-keys params ["CLOJARS_USERNAME" "CLOJARS_PASSWORD"]))))

(c/defpipeline test-and-deploy
  [unit-test
   jar
   deploy])

[test-and-deploy]
