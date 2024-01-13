(ns brainard.infra.routes.ui
  (:require
    [brainard.common.store.specs :as-alias specs]
    [brainard.common.routes.base :as base]
    [brainard.common.routes.interfaces :as iroutes]
    [brainard.common.routes.response :as routes.res]
    [brainard.common.stubs.nav :as nav]
    [brainard.common.utils.routing :as rte]
    [brainard.common.views.pages.core :as pages]
    [brainard.infra.routes.template :as routes.tmpl]
    [clojure.string :as string]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]
    [hiccup.core :as hiccup]
    [ring.middleware.resource :as ring.res]
    [ring.util.mime-type :as ring.mime]
    brainard.common.store.commands
    brainard.common.store.events
    brainard.common.store.queries
    defacto.impl))

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
    (try
      [::res/ok (-> params (assoc :brainard/apis apis) handler :body :data)]
      (catch Throwable ex
        [::res/err (-> ex ex-data :body :errors)]))))

(defn ^:private create-store [{:brainard/keys [apis route]}]
  (let [nav (->StubNav route nil)
        ctx (-> {:services/nav nav}
                (res/with-ctx (->request-fn base/ui-handler apis)))]
    (doto (defacto.impl/->WatchableStore ctx (atom nil) defacto-api ->Sub)
      (->> (defacto/init! nav))
      (defacto/dispatch! [::res/submit! [::specs/notes#buzz]])
      (defacto/dispatch! [::res/submit! [::specs/tags#select]])
      (defacto/dispatch! [::res/submit! [::specs/contexts#select]]))))

(defmethod iroutes/handler [:get :routes/ui]
  [{:brainard/keys [route] :as req}]
  (let [store (create-store req)]
    (routes.res/->response 200
                           (->> [pages/page (assoc route :*:store store)]
                                (routes.tmpl/render store)
                                hiccup/html
                                (str "<!doctype html>"))
                           {"content-type" "text/html"})))

(defmethod iroutes/handler [:get :routes.resources/asset]
  [{:keys [uri] :as req}]
  (let [content-type (or (ring.mime/ext-mime-type uri)
                         "text/plain")]
    (some-> req
            (ring.res/resource-request "public")
            (assoc-in [:headers "content-type"] content-type))))
