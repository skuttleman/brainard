(ns brainard.app
  (:require
    [brainard.infra.store.specs :as specs]
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.stubs.nav :as nav]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.pages.core :as pages]
    [brainard.infra.api :as api]
    [clojure.core.async :as async]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]
    [pushy.core :as pushy]
    [reagent.dom :as rdom]
    brainard.infra.store.commands
    brainard.infra.store.events
    brainard.infra.store.queries))

(deftype NavComponent [^:volatile-mutable -pushy]
  defacto/IInitialize
  (init! [_ store]
    (let [pushy (pushy/pushy #(store/emit! store [:routing/navigated %]) rte/match)]
      (set! -pushy pushy)
      (pushy/start! pushy)))

  nav/INavigate
  (-set! [_ uri]
    (pushy/set-token! -pushy uri))
  (-replace! [_ uri]
    (pushy/replace-token! -pushy uri)))

(defn ->store []
  (store/create (-> {:services/nav (->NavComponent nil)}
                    (res/with-ctx api/request-fn))
                (:init-db dom/env)))

(defn load!
  ([store]
   (load! store (constantly nil)))
  ([store cb]
   (let [root (.getElementById js/document "root")]
     (rdom/render [pages/root store] root cb))))

(defn init!
  "Called when the DOM finishes loading."
  ([]
   (init! (->store)))
  ([store]
   (enable-console-print!)
   (async/go
     (async/<! (async/timeout 15000))
     (defacto/dispatch! store [::res/poll! 15000 [::specs/notes#buzz]]))
   (load! store)))
