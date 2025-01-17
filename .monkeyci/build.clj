(ns build
  (:require [monkey.ci.plugin.clj :as p]
            [monkey.ci.plugin.github :as gh]))

[(p/deps-library)
 (gh/release-job {:dependencies ["publish"]})]
