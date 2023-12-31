(ns brainard.common.routes.notes
  (:require
    [brainard.common.api.core :as api]
    [brainard.common.routes.interfaces :as iroutes])
  #?(:clj
     (:import
       (java.util Date))))

(defmethod iroutes/handler [:get :routes.api/notes?scheduled]
  [{:brainard/keys [apis]}]
  {:status 200
   :body   {:data (api/relevant-notes apis #?(:cljs (js/Date.) :default (Date.)))}})

(defmethod iroutes/req->input [:get :routes.api/notes]
  [{:brainard/keys [route]}]
  (let [{:keys [tags context]} (:query-params route)
        tags (cond
               (coll? tags) (into #{} (map keyword) tags)
               (nil? tags) #{}
               :else #{(keyword tags)})]
    (cond-> {:notes/tags tags}
      context (assoc :notes/context context))))

(defmethod iroutes/handler [:get :routes.api/notes]
  [{:brainard/keys [apis input]}]
  (let [results (api/get-notes apis input)]
    {:status 200
     :body   {:data results}}))


(defmethod iroutes/handler [:post :routes.api/notes]
  [{:brainard/keys [apis input]}]
  {:status 201
   :body   {:data (api/create-note! apis input)}})


(defmethod iroutes/handler [:get :routes.api/note]
  [{:brainard/keys [apis input]}]
  (if-let [note (api/get-note apis (:notes/id input))]
    {:status 200
     :body   {:data note}}
    {:status 404
     :body   {:errors [{:message "Not found"
                        :code    :UNKNOWN_RESOURCE}]}}))


(defmethod iroutes/req->input [:patch :routes.api/note]
  [{:brainard/keys [route] :keys [body]}]
  (assoc body :notes/id (-> route :route-params :notes/id)))

(defmethod iroutes/handler [:patch :routes.api/note]
  [{:brainard/keys [apis input]}]
  (if-let [note (api/update-note! apis (:notes/id input) input)]
    {:status 200
     :body   {:data note}}
    {:status 404
     :body   {:errors [{:message "Not found"
                        :code    :UNKNOWN_RESOURCE}]}}))


(defmethod iroutes/handler [:get :routes.api/tags]
  [{:brainard/keys [apis]}]
  {:status 200
   :body   {:data (api/get-tags apis)}})


(defmethod iroutes/handler [:get :routes.api/contexts]
  [{:brainard/keys [apis]}]
  {:status 200
   :body   {:data (api/get-contexts apis)}})
