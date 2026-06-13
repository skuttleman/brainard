(ns brainard.test
  (:require
   [brainard.api.utils.logger :as log]
   [brainard.app :as app]
   [clojure.core.async :as async]
   [slag.utils.edn :as edn]
   [whet.impl.http :as whttp]))

(def ^:const ^:private DISABLE_SSE
  (edn/read-string (.-DISABLE_SSE js/window)))

(defn ^:export init!
  "Called when the DOM finishes loading."
  []
  (log/info "test app initialized")
  (app/start! app/store->comp {::app/disable-sse? DISABLE_SSE}))

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
