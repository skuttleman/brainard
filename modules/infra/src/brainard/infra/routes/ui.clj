(ns brainard.infra.routes.ui
  (:require
   [brainard :as-alias b]
   [brainard.attachments.api.core :as api.attachments]
   [brainard.infra.routes.core :as routes]
   [brainard.infra.routes.interfaces :as iroutes]
   [brainard.infra.routes.response :as routes.res]
   [brainard.infra.store.specs :as-alias specs]
   [brainard.infra.views.pages.core :as pages]
   [brainard.notes.api.core :as api.notes]
   [brainard.notes.infra.export :as export]
   [clojure.core.async :as async]
   [defacto.core :as defacto]
   [defacto.resources.core :as res]
   [ring.middleware.resource :as ring.res]
   [ring.util.mime-type :as ring.mime]
   [whet.core :as w]
   [whet.impl.template :as tmpl]
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
          :href "https://cdn.lineicons.com/5.1/line/lineicons.css"
          :type "text/css"}])

(def ^:private ^:const css-lib
  [:link {:rel  "stylesheet"
          :href "https://cdnjs.cloudflare.com/ajax/libs/bulma/0.9.4/css/bulma.min.css"
          :type "text/css"}])

(def ^:private unavailable
  [:div#unavailable.layout--stack-between
   (pages/header)
   [:div.message.is-warning
    [:div.message-body
     [:p "This app cannot be rendered on screens/windows this small."]
     [:p "Please increase window size or view on a different device."]]]])

(defn ^:private ui-handler [apis req]
  (-> req
      (assoc ::b/apis apis)
      (routes/handler identity (fn [ex] (throw ex)))))

(defmethod iroutes/handler [:get :routes/ui]
  [{::w/keys [route] ::b/keys [apis env no-hydrate? ui-env]} respond _raise]
  (async/go
    (let [template (if no-hydrate?
                     (tmpl/into-template pages/app-name
                                         (atom nil)
                                         nil
                                         ui-env)
                     (async/<! (w/into-template {::b/sys apis}
                                                pages/app-name
                                                route
                                                (partial ui-handler apis)
                                                (partial store->tree env route)
                                                ui-env)))]
      (respond (routes.res/->response 200
                                      (-> template
                                          (w/with-html-heads icon-lib css-lib)
                                          (update 3 conj unavailable)
                                          w/render-template)
                                      {"content-type" "text/html"})))))

(defmethod iroutes/handler [:get :routes.resources/asset]
  [{:keys [uri] :as req} respond raise]
  (let [content-type (or (ring.mime/ext-mime-type uri)
                         "text/plain")]
    (if-let [response (some-> req
                              (ring.res/resource-request "public")
                              (assoc-in [:headers "content-type"] content-type))]
      (respond response)
      (-> req
          (dissoc ::w/route)
          (iroutes/handler respond raise)))))

(defmethod iroutes/handler [:get :routes.resources/attachment]
  [{::b/keys [apis input] ::w/keys [route] :as req} respond raise]
  (if-let [{:attachments/keys [content-length content-type stream]}
           (api.attachments/fetch (:attachments apis) (:attachments/id input))]
    (respond (routes.res/->response 200
                                    stream
                                    {"content-length" content-length
                                     "content-type"   content-type}))
    (-> req
        (assoc :request-method :get ::w/route (assoc route :token :routes/ui))
        (iroutes/handler respond raise))))

(defmethod iroutes/handler [:get :routes.resources/export]
  [{::b/keys [apis input] ::w/keys [route] :as req} respond raise]
  (if-let [note (api.notes/get-note (:notes apis) input)]
    (let [{:keys [scheme server-name server-port]} req
          host (format "%s://%s:%s" (name scheme) server-name server-port)
          md (export/->markdown host note)]
      (respond (routes.res/->response 200
                                      md
                                      {"content-type"   "text/markdown"
                                       "content-length" (str (count md))})))
    (-> req
        (assoc :request-method :get ::w/route (assoc route :token :routes/ui))
        (iroutes/handler respond raise))))
