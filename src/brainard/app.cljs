(ns brainard.app
  (:require
    [brainard.infra.store.specs :as specs]
    [brainard.resources.system :as res.sys]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.pages.core :as pages]
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

(defn store->comp [store]
  (async/go
    (async/<! (async/timeout 15000))
    (defacto/dispatch! store [::res/poll! 15000 [::specs/notes#buzz]]))
  [pages/root store])

(defn initialize-app [store->comp]
  (-> {:brainard/sys (ig/init res.sys/config [:brainard/workspace-api])}
      (w/with-ctx rte/all-routes)
      (w/render-ui store->comp)))

(defn ^:export init!
  "Called when the DOM finishes loading."
  []
  (initialize-app store->comp))
