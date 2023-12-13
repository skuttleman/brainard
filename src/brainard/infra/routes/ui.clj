(ns brainard.infra.routes.ui
  (:require
    [brainard.common.resources.specs :as-alias rspecs]
    [brainard.common.stubs.nav :as nav]
    [brainard.common.utils.edn :as edn]
    [brainard.common.utils.routing :as rte]
    [brainard.common.views.pages.core :as pages]
    [brainard.infra.routes.errors :as routes.err]
    [brainard.infra.routes.template :as routes.tmpl]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.middleware :as mw]
    [brainard.infra.routes.response :as routes.res]
    [clojure.core.async :as async]
    [clojure.set :as set]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]
    [hiccup.core :as hiccup]
    defacto.impl))

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

(def ^:private ui-handler
  "Resolves \"requests\" made from the ui store during HTML hydration"
  (-> iroutes/handler
      mw/with-spec-validation
      mw/with-input
      mw/with-routing))

(deftype StubNav [route ^:volatile-mutable -store]
  defacto/IInitialize
  (init! [_ store]
    (set! -store store)
    (defacto/emit! -store [:routing/navigated route]))

  nav/INavigate
  (-set! [_ uri]
    (defacto/emit! -store [:routing/navigated (rte/match uri)]))
  (-replace! [this uri]
    (nav/-set! this uri)))

(def ^:private defacto-api
  {:command-handler defacto/command-handler
   :event-reducer   defacto/event-reducer
   :query-responder defacto/query-responder})

(defn ^:private ->Sub [atom-db query]
  (defacto.impl/->StandardSubscription atom-db query defacto/query-responder false))

(defn ^:private ->request-fn [handler apis]
  (fn [_ params]
    (async/go
      (try
        [:ok (:data (:body (handler (assoc params :brainard/apis apis))))]
        (catch Throwable ex
          [:err (:errors (:body (ex-data ex)))])))))

(defn ^:private hydrate [{:brainard/keys [apis route]}]
  (let [handler (cljs-http->ring ui-handler)
        nav (->StubNav route nil)
        ctx {::res/request-fn (->request-fn handler apis)
             :services/nav  nav}
        store (doto (defacto.impl/->WatchableStore ctx (atom nil) defacto-api ->Sub)
                (->> (defacto/init! nav))
                (defacto/dispatch! [::res/submit! ::rspecs/tags#select])
                (defacto/dispatch! [::res/submit! ::rspecs/contexts#select])
                (defacto/dispatch! [::res/submit! ::rspecs/notes#buzz]))]
    (->> (routes.tmpl/render store [pages/page (assoc route :*:store store)])
         hiccup/html
         (str "<!doctype html>"))))

(defmethod iroutes/handler [:get :routes/ui]
  [req]
  (routes.res/->response 200
                         (hydrate req)
                         {"content-type" "text/html"}))
