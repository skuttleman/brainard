(ns brainard.infra.routes.notes
  (:require
    [brainard.api.notes.core :as notes]
    [brainard.infra.routes.common :as routes.common]))

(defmethod routes.common/handler [:get :routes.api/notes]
  [{:brainard/keys [apis] :keys [params]}]
  (let [results (notes/get-notes (:notes apis)
                                 (cond-> params
                                   (:tag params) (update :tag keyword)))]
    {:status 200
     :body   {:results results}}))

(defmethod routes.common/handler [:post :routes.api/notes]
  [{:brainard/keys [apis] :keys [body]}]
  {:status 201
   :body {:notes/id (notes/take-note! (:notes apis) body)}})

(defmethod routes.common/handler [:get :routes.api/tags]
  [{:brainard/keys [apis]}]
  {:status 200
   :body {:notes/tags (notes/get-tags (:notes apis))}})

(defmethod routes.common/handler [:get :routes.api/contexts]
  [{:brainard/keys [apis]}]
  {:status 200
   :body   {:notes/contexts (notes/get-contexts (:notes apis))}})
