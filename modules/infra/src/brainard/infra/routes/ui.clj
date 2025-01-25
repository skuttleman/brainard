(ns brainard.infra.routes.ui
  (:require
    [brainard :as-alias b]
    [brainard.attachments.api.core :as api.attachments]
    [brainard.infra.routes.core :as routes]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.response :as routes.res]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.pages.core :as pages]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]
    [ring.middleware.resource :as ring.res]
    [ring.util.mime-type :as ring.mime]
    [whet.core :as w]
    brainard.infra.store.commands
    brainard.infra.store.events
    brainard.infra.store.queries))

(defn ^:private store->tree [env route store]
  (-> store
      (defacto/dispatch! [::res/submit! [::specs/notes#buzz]])
      (defacto/dispatch! [::res/submit! [::specs/tags#select]])
      (defacto/dispatch! [::res/submit! [::specs/contexts#select]])
      (defacto/emit! [::w/in-env (or env :prod)]))
  [pages/page store route])

(def ^:private ^:const icon-lib
  [:link {:rel  "stylesheet"
          :href "https://cdn.lineicons.com/4.0/lineicons.css"
          :type "text/css"}])

(def ^:private ^:const css-lib
  [:link {:rel  "stylesheet"
          :href "https://cdnjs.cloudflare.com/ajax/libs/bulma/0.9.4/css/bulma.min.css"
          :type "text/css"}])

(defn ^:private ui-handler [apis req]
  (-> req
      (assoc ::b/apis apis)
      routes/handler))

(defmethod iroutes/handler [:get :routes/ui]
  [{::w/keys [route] ::b/keys [apis env]}]
  (let [template (-> {::b/sys apis}
                     (w/into-template "brainard"
                                      route
                                      (partial ui-handler apis)
                                      (partial store->tree env route))
                     (w/with-html-heads icon-lib css-lib))]
    (routes.res/->response 200
                           (w/render-template template)
                           {"content-type" "text/html"})))

(defmethod iroutes/handler [:get :routes.resources/asset]
  [{:keys [uri] :as req}]
  (let [content-type (or (ring.mime/ext-mime-type uri)
                         "text/plain")]
    (some-> req
            (ring.res/resource-request "public")
            (assoc-in [:headers "content-type"] content-type))))

(defmethod iroutes/handler [:get :routes.resources/attachment]
  [{::b/keys [apis input] ::w/keys [route] :as req}]
  (if-let [{:attachments/keys [content-length content-type stream]}
           (api.attachments/fetch (:attachments apis) (:attachments/id input))]
    (routes.res/->response 200
                           stream
                           {"content-length" content-length
                            "content-type"   content-type})
    (iroutes/handler (assoc req
                            :request-method :get
                            ::w/route (assoc route :token :routes/ui)))))
