(ns brainard.events.infra.handler
  (:require
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.utils.edn :as edn]
    [defacto.core :as defacto]
    [defacto.resources.core :as-alias res]))

(defn ^:private e->clj [e]
  (edn/read-string (.-data e)))

(defmulti ^:private event-handler
          (fn [_ [type]]
            type))

(defmethod event-handler :notes/relevant
  [store [_ {:keys [data]}]]
  (defacto/emit! store [::res/swapped [::specs/notes#buzz] data]))

(defn ->event-handler
  "Creates an SSE event handler"
  [store]
  (fn [e]
    (event-handler store (e->clj e))))
