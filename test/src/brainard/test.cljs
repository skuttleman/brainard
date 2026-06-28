(ns brainard.test
  (:require
   [brainard.api.utils.logger :as log]
   [brainard.app :as app]
   [brainard.events.infra.handler :as handler]
   [clojure.core.async :as async]
   [defacto.resources.core :as-alias res]
   [slag.utils.edn :as edn]
   [whet.impl.http :as whttp]))

(defonce ^:dynamic *store* nil)

(def ^:const ^:private DISABLE_SSE
  (edn/read-string (.-DISABLE_SSE js/window)))

(defn ^:private http-mw [handler]
  (fn [resource-type params]
    (async/go
     (log/info "sending HTTP request" params)
     (let [[status result] (async/<! (handler resource-type params))]
       (if (= status ::res/ok)
         (log/info "HTTP success" params result)
         (log/error "HTTP error" params result))
       [status result]))))

(defn ^:private set-store! [store]
  (set! *store* store)
  store)

(defn ^:export init!
  "Called when the DOM finishes loading."
  []
  (log/info "test app initialized")
  (app/start! (comp app/store->comp set-store!)
              {::app/disable-sse? DISABLE_SSE
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

(defn ^:export on-event! [event]
  (handler/event-handler *store* (edn/read-string event)))
