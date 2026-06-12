(ns brainard.test.cljs.executor
  (:require
   [clojure.test :as t]
   [slag.test.utils.executor :as exec]
   brainard.api.validations-test
   brainard.infra.routes.errors-test
   brainard.infra.store-test
   brainard.infra.store.commands-test
   brainard.infra.store.events-test
   brainard.infra.store.queries-test
   brainard.infra.store.specs-test
   brainard.schedules.api.relevancy-test))

(defn ^:export test! []
  (exec/run-tests! (fn []
                     (t/run-tests
                      'brainard.api.validations-test
                      'brainard.infra.routes.errors-test
                      'brainard.infra.store-test
                      'brainard.infra.store.commands-test
                      'brainard.infra.store.events-test
                      'brainard.infra.store.queries-test
                      'brainard.infra.store.specs-test
                      'brainard.schedules.api.relevancy-test))))
