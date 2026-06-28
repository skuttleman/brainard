(ns brainard.events.infra.handler
  (:require
   [brainard.api.utils.logger :as log]
   [brainard.infra.store.core :as store]
   [brainard.infra.store.specs :as-alias specs]
   [defacto.core :as defacto]
   [defacto.resources.async :as-alias res.async]
   [defacto.resources.core :as-alias res]
   [slag.utils.edn :as edn]))

(defn ^:private e->clj [e]
  (edn/read-string (.-data e)))

(def ^:private event-hierarchy
  (-> (make-hierarchy)
      (derive :workspace/tree ::async)))

(defmulti ^:private event-handler
          (fn [_ [type]]
            type)
          :hierarchy
          #'event-hierarchy)

(defmethod event-handler :default
  [_ [type]]
  (log/info "Unknown SSE type:" type))

(defn ->event-handler
  "Creates an SSE event handler"
  [store]
  (fn [e]
    (event-handler store (e->clj e))))

(defmethod event-handler :notes/relevant
  [store [_ {:keys [data]}]]
  (defacto/emit! store [::res/swapped [::specs/notes#buzz] data]))

(defmethod event-handler ::async
  [store [_ {:keys [request-id] :as result}]]
  (store/dispatch! store [::res.async/receive! request-id result]))
