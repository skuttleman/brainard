(ns brainard.common.store.queries
  (:require
    [defacto.core :as defacto]))

(defmethod defacto/query :routing/?route
  [db _]
  (:routing/info db))

(defmethod defacto/query :resources/?resource
  [db [_ handle]]
  (get-in db [:resources/resources handle] [:init]))

(defmethod defacto/query :forms/?form
  [db [_ form-id]]
  (get-in db [:forms/forms form-id]))

(defmethod defacto/query :toasts/?toasts
  [db _]
  (->> (:toasts/toasts db)
       (sort-by key)
       (take 3)
       (map (fn [[toast-id toast]]
              (assoc toast :id toast-id)))))
