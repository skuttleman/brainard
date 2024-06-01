(ns brainard.notes.infra.routes
  (:require
    [brainard :as-alias b]
    [brainard.api.core :as api]
    [brainard.infra.routes.interfaces :as iroutes]
    [whet.core :as w])
  #?(:clj
     (:import
       (java.util Date))))

(defmethod iroutes/handler [:get :routes.api/notes?scheduled]
  [{::b/keys [apis]}]
  {:status 200
   :body   {:data (api/relevant-notes apis #?(:cljs (js/Date.) :default (Date.)))}})

(defmethod iroutes/handler [:get :routes.api/notes?pinned]
  [{::b/keys [apis]}]
  {:status 200
   :body   {:data (api/get-notes apis {:notes/pinned? true})}})

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
    {:status 200
     :body   {:data results}}))


(defmethod iroutes/handler [:post :routes.api/notes]
  [{::b/keys [apis input]}]
  {:status 201
   :body   {:data (api/create-note! apis input)}})


(defmethod iroutes/handler [:get :routes.api/note]
  [{::b/keys [apis input]}]
  (if-let [note (api/get-note apis (:notes/id input))]
    {:status 200
     :body   {:data note}}
    {:status 404
     :body   {:errors [{:message "Not found"
                        :code    :UNKNOWN_RESOURCE}]}}))


(defmethod iroutes/req->input [:patch :routes.api/note]
  [{::w/keys [route] :keys [body]}]
  (assoc body :notes/id (-> route :route-params :notes/id)))

(defmethod iroutes/handler [:patch :routes.api/note]
  [{::b/keys [apis input]}]
  (if-let [note (api/update-note! apis (:notes/id input) input)]
    {:status 200
     :body   {:data note}}
    {:status 404
     :body   {:errors [{:message "Not found"
                        :code    :UNKNOWN_RESOURCE}]}}))


(defmethod iroutes/handler [:get :routes.api/tags]
  [{::b/keys [apis]}]
  {:status 200
   :body   {:data (api/get-tags apis)}})


(defmethod iroutes/handler [:get :routes.api/contexts]
  [{::b/keys [apis]}]
  {:status 200
   :body   {:data (api/get-contexts apis)}})
