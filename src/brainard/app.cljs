(ns brainard.app
  (:require
    [brainard.api.utils.logger :as log]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.pages.core :as pages]
    [brainard.notifications.infra.manager :as manager]
    [defacto.resources.core :as-alias res]
    [whet.core :as w]
    brainard.infra.store.commands
    brainard.infra.store.events
    brainard.infra.store.queries))

(enable-console-print!)

(defn store->comp
  "Takes initialized defacto store and returns the component tree"
  [store]
  (manager/loop! store)
  (dom/add-listener! js/navigation
                     :navigate
                     (fn [^js/NavigateEvent e]
                       (when (= "traverse" (.-navigationType e))
                         (store/emit! store [:modals/all-destroyed]))))
  [pages/root store])

(defn start!
  "Starts the reagent app"
  ([store->comp]
   (start! store->comp nil))
  ([store->comp opts]
   (w/render-ui (w/with-ctx {} rte/all-routes) store->comp opts)))

(defn ^:export init!
  "Called when the DOM finishes loading."
  []
  (start! store->comp)
  (log/info "app initialized"))
