(ns brainard.test
  (:require
   [brainard.api.utils.logger :as log]
   [brainard.app :as app]
   [clojure.core.async :as async]
   [slag.utils.edn :as edn]
   [whet.impl.http :as whttp]))

(def ^:const ^:private DISABLE_SSE
  (edn/read-string (.-DISABLE_SSE js/window)))

(defn ^:private http-mw [handler]
  (fn [resource-type ctx-map params]
    (async/go
     (log/info "sending HTTP request" resource-type params)
     (let [result (async/<! (handler resource-type ctx-map params))]
       (log/info "receiving HTTP response" resource-type params result)
       result))))

(defn ^:export init!
  "Called when the DOM finishes loading."
  []
  (log/info "test app initialized")
  (app/start! app/store->comp {::app/disable-sse? DISABLE_SSE
                               :request-mw        http-mw}))

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
