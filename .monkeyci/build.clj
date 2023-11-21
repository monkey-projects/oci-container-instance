(ns container-instance.build
  (:require [monkey.ci.build
             [api :as api]
             [core :as c]
             [shell :as s]]))

(defn clj [name & args]
  "Executes script in clojure container"
  {:name name
   :container/image "docker.io/clojure:temurin-21-tools-deps-alpine"
   :script ["ls -al"
            "df -h"
            (apply str "clojure " args)]})

(def inspect
  (s/bash "ls -al && df -h"))

(def unit-test (clj "unit-test" "-X:test:junit"))
(def jar (clj "jar" "-X:jar"))

(defn deploy [ctx]
  (-> (api/build-params ctx)
      (select-keys ["CLOJARS_USERNAME" "CLOJARS_PASSWORD"])
      (as-> p (assoc (clj "deploy" "-X:jar:deploy")
                     :container/env p))))

(c/defpipeline test-and-deploy
  [inspect
   unit-test
   jar
   deploy])

[test-and-deploy]
