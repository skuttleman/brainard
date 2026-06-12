(ns brainard.test
  (:require
   [brainard.api.utils.logger :as log]
   [brainard.app :as app]
   [clojure.core.async :as async]
   [whet.impl.http :as whttp]))

(defn ^:export init!
  "Called when the DOM finishes loading."
  []
  (log/info "test app initialized")
  (app/start! app/store->comp))

(defn ^:export set-fail! [status msg]
  (let [resp {:status  status
              :headers {"content-type" "application/edn"}
              :body    {:errors [{:message msg
                                  :code    :UI_TEST_FAILURE_SIMULATION}]}}]
    (set! whttp/*req-middleware*
          [(fn [_request-fn]
             (fn [_req]
               (async/go resp)))])))

(defn ^:export clear-fail! []
  (set! whttp/*req-middleware* []))
