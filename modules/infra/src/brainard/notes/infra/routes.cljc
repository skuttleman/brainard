(ns brainard.notes.infra.routes
  (:require
   [brainard :as-alias b]
   [brainard.infra.routes.interfaces :as iroutes]
   [whet.core :as w])
  #?(:clj
     (:import
      (java.util Date))))

(defmethod iroutes/req->input [:get :routes.api/notes?scheduled]
  [_]
  {:timestamp #?(:cljs (js/Date.) :default (Date.))})

(defmethod iroutes/req->input [:get :routes.api/notes]
  [{::w/keys [route]}]
  (let [{:keys [archived body context pinned tags todos]} (:query-params route)
        tags (cond
               (coll? tags) (into #{} (map keyword) tags)
               (nil? tags) #{}
               :else #{(keyword tags)})]
    (cond-> {}
      (seq tags) (assoc :notes/tags tags)
      body (assoc :notes/body body)
      context (assoc :notes/context context)
      todos (assoc :notes/todos (keyword todos))
      archived (assoc :notes/archived (keyword archived))
      pinned (assoc :notes/pinned? pinned))))
