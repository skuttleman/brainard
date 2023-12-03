(ns brainard.common.store.queries
  (:require
    [yast.core :as yast]))

(defmethod yast/query :routing/?route
  [db _]
  (:routing/info db))

(defmethod yast/query :resources/?resource
  [db [_ handle]]
  (get-in db [:resources/resources handle] [:init]))

(defmethod yast/query :forms/?form
  [db [_ form-id]]
  (get-in db [:forms/forms form-id]))

(defmethod yast/query :toasts/?toasts
  [db _]
  (->> (:toasts/toasts db)
       (sort-by key)
       (take 3)
       (map (fn [[toast-id toast]]
              (assoc toast :id toast-id)))))
