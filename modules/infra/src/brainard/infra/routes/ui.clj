(ns brainard.infra.routes.ui
  (:require
    [brainard.common.utils.edn :as edn]
    [brainard.common.views.pages.core :as pages]
    [brainard.infra.routes.errors :as routes.err]
    [brainard.infra.routes.template :as routes.tmpl]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.middleware :as mw]
    [brainard.infra.routes.response :as routes.res]
    [clojure.set :as set]
    [defacto.core :as defacto]
    [hiccup.core :as hiccup])
  (:import (clojure.lang IDeref IRef)))

(defn ^:private cljs-http->ring [handler]
  (fn [req]
    (try
      (let [response (-> req
                         (set/rename-keys {:url :uri})
                         (update :body edn/read-string)
                         handler)]
        (cond-> response
          (string? (:body response)) (update :body edn/read-string)))
      (catch Throwable ex
        (routes.err/ex->response (ex-data ex))))))

(deftype UIStore [db ctx]
  IDeref
  (deref [_] @db)

  defacto/IStore
  (-dispatch! [this command]
    (defacto/command-handler (assoc ctx ::defacto/store this)
                             command
                             (fn [event]
                               (vswap! db defacto/event-handler event)
                               nil)))
  (-subscribe [_ query]
    (reify
      IDeref
      (deref [_] (defacto/query @db query))

      IRef
      (addWatch [this _ _] this)
      (removeWatch [this _] this))))

(def ^:private ui-handler
  "Resolves \"requests\" made from the ui store during HTML hydration"
  (-> iroutes/handler
      mw/with-spec-validation
      (mw/with-input {:req->input iroutes/req->input})
      mw/with-routing))

(defn ^:private hydrate [{:brainard/keys [apis route]}]
  (let [handler (cljs-http->ring ui-handler)
        store (doto (->UIStore (volatile! nil)
                               {:services/http (fn [req]
                                                 (-> req
                                                     (assoc :brainard/apis apis)
                                                     handler))})
                (defacto/dispatch! [:resources/ensure! :api.tags/select!])
                (defacto/dispatch! [:resources/ensure! :api.contexts/select!]))]
    (->> (routes.tmpl/render store [pages/page store route])
         hiccup/html
         (str "<!doctype html>"))))

(defmethod iroutes/handler [:get :routes/ui]
  [req]
  (routes.res/->response 200
                         (hydrate req)
                         {"content-type" "text/html"}))
