(ns brainard.app
  (:require
   [brainard.api.utils.logger :as log]
   [brainard.infra.store.core :as store]
   [brainard.infra.store.specs :as-alias specs]
   [brainard.infra.stubs.dom :as dom]
   [brainard.infra.utils.routing :as rte]
   [brainard.infra.views.pages.core :as pages]
   [brainard.events.infra.handler :as handler]
   [brainard.infra.views.pages.interfaces :as ipages]
   [defacto.resources.core :as-alias res]
   [whet.core :as w]
   [whet.utils.navigation :as nav]
   brainard.infra.store.commands
   brainard.infra.store.events
   brainard.infra.store.queries))

(enable-console-print!)

(defn ^:private ->EventSource [route]
  (js/EventSource. (nav/path-for rte/all-routes route)))

(defn ^:private with-stream! [store]
  (log/info "connecting event stream...")
  (doto (->EventSource :routes.api/events)
    (dom/add-listener! :open (fn [_] (log/info "event stream connected")))
    (dom/add-listener! :error (fn [_] (log/warn "event stream closed")))
    (dom/add-listener! :message (handler/->event-handler store)))
  store)

(defn ^:private with-nav! [store]
  (log/info "closing modals on navigation...")
  (dom/add-listener! js/navigation
                     :navigate
                     (fn [^js/NavigateEvent e]
                       (when (= "traverse" (.-navigationType e))
                         (store/emit! store [:modals/all-destroyed]))))
  store)

(defn ^:private with-kb! [store]
  (letfn [(e->dispatch [e]
            [(dom/event->modifiers e) (dom/event->key e)])]
    (doto dom/window
      (dom/add-listener! :keydown
                         (fn [e]
                           (when (contains? (methods ipages/kb-shortcut!) (e->dispatch e))
                             (dom/prevent-default! e))))
      (dom/add-listener! :keyup
                         (fn [e]
                           (when-let [[dispatch] (find (methods ipages/kb-shortcut!) (e->dispatch e))]
                             (ipages/kb-shortcut! dispatch store)))))
    store))

(defn store->comp
  "Takes initialized defacto store and returns the component tree"
  [store]
  (-> store
      (store/dispatch! [:local-storage/init!])
      with-nav!
      with-kb!)
  [pages/root store])

(defn start!
  "Starts the reagent app"
  ([store->comp]
   (start! store->comp nil))
  ([store->comp opts]
   (let [store->comp (cond-> store->comp
                       (not (::disable-sse? opts)) (comp with-stream!))]
     (w/render-ui (w/with-ctx {} rte/all-routes) store->comp opts))))

(defn ^:export init!
  "Called when the DOM finishes loading."
  []
  (start! store->comp)
  (log/info "app initialized"))
