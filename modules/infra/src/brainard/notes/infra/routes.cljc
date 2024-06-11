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
  (let [{:keys [context pinned tags]} (:query-params route)
        tags (cond
               (coll? tags) (into #{} (map keyword) tags)
               (nil? tags) #{}
               :else #{(keyword tags)})]
    (cond-> {:notes/tags tags}
      context (assoc :notes/context context)
      (= pinned "true") (assoc :notes/pinned? true))))
