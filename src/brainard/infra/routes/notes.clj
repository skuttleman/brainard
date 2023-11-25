(ns brainard.infra.routes.notes
  (:require
    [brainard.api.notes.core :as notes]
    [brainard.infra.routes.common :as routes.common]
    [clj-uuid :as uuid]))

(defmethod routes.common/coerce [:get :routes.api/notes]
  [{:keys [params]}]
  (let [{:keys [tag context]} params
        tags (cond
               (coll? tag) tag
               (nil? tag) []
               :else [tag])]
    (cond-> {:notes/tags (into #{} (map keyword) tags)}
      context (assoc :notes/context context))))

(defmethod routes.common/handler [:get :routes.api/notes]
  [{:brainard/keys [apis input]}]
  (let [results (notes/get-notes (:notes apis) input)]
    {:status 200
     :body   {:data results}}))


(defmethod routes.common/handler [:post :routes.api/notes]
  [{:brainard/keys [apis input]}]
  {:status 201
   :body   {:data (notes/create! (:notes apis) input)}})


(defmethod routes.common/coerce [:patch :routes.api/note]
  [{:brainard/keys [route] :keys [body]}]
  (assoc body
         :notes/id (-> route :route-params :notes/id uuid/as-uuid)))

(defmethod routes.common/handler [:patch :routes.api/note]
  [{:brainard/keys [apis input]}]
  (if-let [note (notes/update! (:notes apis)
                               (:notes/id input)
                               input)]
    {:status 200
     :body   {:data note}}
    {:status 404
     :body   {:errors [{:message "Not found"
                        :code    :UNKNOWN_RESOURCE}]}}))


(defmethod routes.common/handler [:get :routes.api/tags]
  [{:brainard/keys [apis]}]
  {:status 200
   :body   {:data (notes/get-tags (:notes apis))}})


(defmethod routes.common/handler [:get :routes.api/contexts]
  [{:brainard/keys [apis]}]
  {:status 200
   :body   {:data (notes/get-contexts (:notes apis))}})
