(ns brainard.app
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.pages.core :as pages]
    [clojure.core.async :as async]
    [defacto.core :as defacto]
    [defacto.resources.core :as-alias res]
    [whet.core :as w]
    brainard.infra.store.commands
    brainard.infra.store.events
    brainard.infra.store.queries
    brainard.infra.system))

(enable-console-print!)

(defn store->comp
  "Takes initialized defacto store and returns the component tree"
  [store]
  (async/go
    (async/<! (async/timeout 15000))
    (defacto/dispatch! store [::res/poll! 15000 [::specs/notes#buzz]]))
  (dom/add-listener! js/navigation
                     :navigate
                     (fn [^js/NavigateEvent e]
                       (when (= "traverse" (.-navigationType e))
                         (store/dispatch! store [:modals/remove-all!]))))
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
  (start! store->comp))
