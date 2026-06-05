(ns brainard.notifications.infra.manager
  (:require
    [brainard.api.utils.logger :as log]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.utils.edn :as edn]
    [defacto.core :as defacto]
    [defacto.resources.core :as-alias res]))

(defmulti ^:private event-handler*
          (fn [_ [type]]
            type))

(defn ^:private e->clj [e]
  (edn/read-string (.-data e)))

(defn ^:private ->event-handler [store]
  (fn [e]
    (event-handler* store (e->clj e))))

(defn loop!
  "Establishes event stream connection"
  [store]
  (log/info "connecting event stream...")
  (doto (js/EventSource. "/api/ws")
    (dom/add-listener! :open (fn [_] (log/info "event stream connected")))
    (dom/add-listener! :error (fn [_] (log/warn "event stream closed")))
    (dom/add-listener! :message (->event-handler store))))

(defmethod event-handler* :notes/relevant
  [store [_ {:keys [data]}]]
  (defacto/emit! store [::res/swapped [::specs/notes#buzz] data]))
