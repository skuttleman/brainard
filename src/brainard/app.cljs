(ns brainard.app
  (:require
    [brainard.infra.store.specs :as specs]
    [brainard.infra.store.core :as store]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.pages.core :as pages]
    [brainard.infra.api :as api]
    [clojure.core.async :as async]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]
    brainard.infra.store.commands
    brainard.infra.store.events
    brainard.infra.store.queries))

(defn init!
  "Called when the DOM finishes loading."
  [])
