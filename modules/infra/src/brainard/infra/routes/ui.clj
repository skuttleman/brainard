(ns brainard.infra.routes.ui
  (:require
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.routes.core :as routes]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.response :as routes.res]
    [whet.core :as w]
    [brainard.infra.views.pages.core :as pages]
    [defacto.core :as defacto]
    [defacto.resources.core :as res]
    [ring.middleware.resource :as ring.res]
    [ring.util.mime-type :as ring.mime]
    brainard.infra.store.commands
    brainard.infra.store.events
    brainard.infra.store.queries))

(defn ^:private store->tree [route store]
  (doto store
    (defacto/dispatch! [::res/submit! [::specs/notes#buzz]])
    (defacto/dispatch! [::res/submit! [::specs/tags#select]])
    (defacto/dispatch! [::res/submit! [::specs/contexts#select]]))
  [pages/page (assoc route :*:store store)])

(def ^:private ^:const font-awesome
  [:link {:rel         "stylesheet"
          :href        "https://use.fontawesome.com/releases/v5.6.3/css/all.css"
          :integrity   "sha384-UHRtZLI+pbxtHCWp1t77Bi1L4ZtiqrqD80Kn4Z8NTSRyMA2Fd33n5dQ8lWUE00s/"
          :crossorigin "anonymous"
          :type        "text/css"}])

(def ^:private ^:const bulma
  [:link {:rel  "stylesheet"
          :href "https://cdnjs.cloudflare.com/ajax/libs/bulma/0.9.4/css/bulma.min.css"
          :type "text/css"}])


(defmethod iroutes/handler [:get :routes/ui]
  [{::w/keys [route] :brainard/keys [apis] :as req}]
  (let [template (-> route
                     (w/into-template (fn [req]
                                        (-> req
                                            (assoc :brainard/apis apis)
                                            routes/handler))
                                      (partial store->tree route))
                     (w/with-html-headers font-awesome bulma))]
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
