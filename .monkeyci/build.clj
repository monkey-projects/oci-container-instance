(ns container-instance.build
  (:require [monkey.ci.build.core :as c]))

(defn clj [name & args]
  "Executes script in clojure container"
  {:name name
   :container/image "docker.io/clojure:temurin-20-tools-deps-alpine"
   :script [(apply str "clojure " args)]})

(def unit-test (clj "unit-test" "-X:test:junit"))
(def jar (clj "jar" "-X:jar"))
(def deploy (clj "deploy" "-X:jar:deploy"))

(def test-and-deploy
  (c/pipeline
   {:name "test-and-deploy"
    :steps [unit-test
            jar
            #_deploy]}))

[test-and-deploy]
