(ns brainard.test.runner
  (:require
    [slag.test.utils.runner :as runner]))

(defn -main []
  (runner/run-tests! "/cljs-test/test.js" "target/cljs-test/test.js"))
