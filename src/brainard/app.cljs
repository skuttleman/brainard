(ns brainard.app
  (:require
    [brainard.infra.store.specs :as specs]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.pages.core :as pages]
    [clojure.core.async :as async]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]
    [whet.core :as w]
    brainard.infra.store.commands
    brainard.infra.store.events
    brainard.infra.store.queries))

(defn on-rendered
  "call after the reagent app is rendered"
  [store]
  (async/go
    (async/<! (async/timeout 15000))
    (defacto/dispatch! store [::res/poll! 15000 [::specs/notes#buzz]])))

(defn ^:export init!
  "Called when the DOM finishes loading."
  []
  (enable-console-print!)
  (w/render-ui rte/all-routes pages/root on-rendered))
