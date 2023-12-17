(ns brainard.app
  (:require
    [brainard.common.resources.specs :as rspecs]
    [brainard.common.store.core :as store]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.nav :as nav]
    [brainard.common.utils.routing :as rte]
    [brainard.common.views.pages.core :as pages]
    [clojure.core.async :as async]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]
    [pushy.core :as pushy]
    [reagent.dom :as rdom]
    [brainard.infra.api.http :as be]
    brainard.common.store.commands
    brainard.common.store.events
    brainard.common.store.queries))

(def ^:private ^:dynamic *store*)

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

(defmethod defacto/event-reducer ::reset
  [_ [_ new-db]]
  new-db)

(defn ^:private load* [cb]
  (let [root (.getElementById js/document "root")]
    (rdom/render [pages/root *store*] root cb)))

(defn load!
  "Called when new code is compiled in the browser."
  []
  (let [db-value @*store*]
    (load* (fn []
             (store/emit! *store* [::reset db-value])))))

(defn init!
  "Called when the DOM finishes loading."
  []
  (enable-console-print!)
  (set! *store* (store/create (-> {:services/nav (->NavComponent nil)}
                                  (res/with-ctx be/request-fn))
                              (:init-db dom/env)))
  #_(async/go
    (async/<! (async/timeout 15000))
    (defacto/dispatch! *store* [::res/poll! 15000 [::rspecs/notes#buzz]]))
  (load* (constantly nil)))
