(ns brainard.app
  (:require
    [brainard.infra.store.specs :as-alias specs]
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
  ""
  [store]
  (async/go
    (async/<! (async/timeout 15000))
    (defacto/dispatch! store [::res/poll! 15000 [::specs/notes#buzz]]))
  [pages/root store])

(defn start!
  ""
  [store->comp]
  (w/render-ui (w/with-ctx {} rte/all-routes) store->comp))

(defn ^:export init!
  "Called when the DOM finishes loading."
  []
  (start! store->comp))
