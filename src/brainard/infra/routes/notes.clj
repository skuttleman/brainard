(ns brainard.infra.routes.notes
  (:require
    [brainard.api.notes.core :as notes]
    [brainard.infra.routes.interfaces :as iroutes]))

(defmethod iroutes/req->input [:get :routes.api/notes]
  [{:keys [params]}]
  (let [{:keys [tags context]} params
        tags (cond
               (coll? tags) (into #{} (map keyword) tags)
               (nil? tags) #{}
               :else #{(keyword tags)})]
    (cond-> {:notes/tags tags}
      context (assoc :notes/context context))))

(defmethod iroutes/handler [:get :routes.api/notes]
  [{:brainard/keys [apis input]}]
  (let [results (notes/get-notes (:notes apis) input)]
    {:status 200
     :body   {:data results}}))


(defmethod iroutes/handler [:post :routes.api/notes]
  [{:brainard/keys [apis input]}]
  {:status 201
   :body   {:data (notes/create! (:notes apis) input)}})


(defmethod iroutes/req->input [:patch :routes.api/note]
  [{:brainard/keys [route] :keys [body]}]
  (assoc body :notes/id (-> route :route-params :notes/id)))

(defmethod iroutes/handler [:patch :routes.api/note]
  [{:brainard/keys [apis input]}]
  (if-let [note (notes/update! (:notes apis)
                               (:notes/id input)
                               input)]
    {:status 200
     :body   {:data note}}
    {:status 404
     :body   {:errors [{:message "Not found"
                        :code    :UNKNOWN_RESOURCE}]}}))


(defmethod iroutes/handler [:get :routes.api/tags]
  [{:brainard/keys [apis]}]
  {:status 200
   :body   {:data (notes/get-tags (:notes apis))}})


(defmethod iroutes/handler [:get :routes.api/contexts]
  [{:brainard/keys [apis]}]
  {:status 200
   :body   {:data (notes/get-contexts (:notes apis))}})
