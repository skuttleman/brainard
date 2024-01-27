(ns brainard.app
  (:require
    [brainard.infra.store.specs :as specs]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.pages.core :as pages]
    [brainard.resources.system :as res.sys]
    [clojure.core.async :as async]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]
    [integrant.core :as ig]
    [whet.core :as w]
    brainard.infra.store.commands
    brainard.infra.store.events
    brainard.infra.store.queries
    brainard.infra.system
    brainard.workspace.infra.store))

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
  [sys store->comp]
  (-> {:brainard/sys sys}
      (w/with-ctx rte/all-routes)
      (w/render-ui store->comp)))

(defn ->system
  ""
  []
  (ig/init res.sys/config [:brainard/workspace-api]))

(defn ^:export init!
  "Called when the DOM finishes loading."
  []
  (start! (->system) store->comp))
