(ns brainard.common.store.queries
  (:require
    [defacto.core :as defacto]))

(defmethod defacto/query-handler :routing/route
  [db _]
  (:routing/info db))

(defmethod defacto/query-handler :resources/resource
  [db [_ handle]]
  (get-in db [:resources/resources handle] [:init]))

(defmethod defacto/query-handler :forms/form
  [db [_ form-id]]
  (get-in db [:forms/forms form-id]))

(defmethod defacto/query-handler :toasts/toasts
  [db _]
  (->> (:toasts/toasts db)
       (sort-by key)
       (take 3)
       (map (fn [[toast-id toast]]
              (assoc toast :id toast-id)))))
