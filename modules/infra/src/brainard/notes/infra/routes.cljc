(ns brainard.notes.infra.routes
  (:require
    [brainard :as-alias b]
    [brainard.api.core :as api]
    [brainard.infra.routes.interfaces :as iroutes]
    [brainard.infra.routes.response :as routes.res]
    [whet.core :as w])
  #?(:clj
     (:import
       (java.util Date))))

(def ^:private ^:const not-found
  (routes.res/->response 404 {:errors [{:message "Not found"
                                        :code    :UNKNOWN_RESOURCE}]}))

(defmethod iroutes/handler [:get :routes.api/notes?scheduled]
  [{::b/keys [apis]}]
  (let [results (api/relevant-notes apis #?(:cljs    (js/Date.)
                                            :default (Date.)))]
    (routes.res/->response 200
                           {:data results})))

(defmethod iroutes/handler [:get :routes.api/notes?pinned]
  [{::b/keys [apis]}]
  (let [results (api/get-notes apis {:notes/pinned? true})]
    (routes.res/->response 200 {:data results})))

(defmethod iroutes/req->input [:get :routes.api/notes]
  [{::w/keys [route]}]
  (let [{:keys [context pinned tags]} (:query-params route)
        tags (cond
               (coll? tags) (into #{} (map keyword) tags)
               (nil? tags) #{}
               :else #{(keyword tags)})]
    (cond-> {:notes/tags tags}
      context (assoc :notes/context context)
      (= pinned "true") (assoc :notes/pinned? true))))

(defmethod iroutes/handler [:get :routes.api/notes]
  [{::b/keys [apis input]}]
  (let [results (api/get-notes apis input)]
    (routes.res/->response 200 {:data results})))


(defmethod iroutes/handler [:post :routes.api/notes]
  [{::b/keys [apis input]}]
  (let [result (api/create-note! apis input)]
    (routes.res/->response 201 {:data result})))


(defmethod iroutes/handler [:get :routes.api/note]
  [{::b/keys [apis input]}]
  (if-let [note (api/get-note apis (:notes/id input))]
    (routes.res/->response 200 {:data note})
    not-found))


(defmethod iroutes/req->input [:patch :routes.api/note]
  [{::w/keys [route] :keys [body]}]
  (assoc body :notes/id (-> route :route-params :notes/id)))

(defmethod iroutes/handler [:patch :routes.api/note]
  [{::b/keys [apis input]}]
  (if-let [note (api/update-note! apis (:notes/id input) input)]
    (routes.res/->response 200 {:data note})
    not-found))


(defmethod iroutes/handler [:delete :routes.api/note]
  [{::b/keys [apis input]}]
  (api/delete-note! apis (:notes/id input))
  (routes.res/->response 204))


(defmethod iroutes/handler [:get :routes.api/tags]
  [{::b/keys [apis]}]
  (let [results (api/get-tags apis)]
    (routes.res/->response 200 {:data results})))


(defmethod iroutes/handler [:get :routes.api/contexts]
  [{::b/keys [apis]}]
  (let [results (api/get-contexts apis)]
    (routes.res/->response 200 {:data results})))
