(ns brainard.common.store.queries
  (:require
    [defacto.core :as defacto]))

(defmethod defacto/query-responder :app/?:loading
  [db _]
  (:app/loading? db false))

(defmethod defacto/query-responder :routing/?:route
  [db _]
  (:routing/info db))

(defmethod defacto/query-responder :modals/?:modals
  [db _]
  (->> (:modals/modals db)
       (sort-by key >)
       (map (fn [[modal-id modal]]
              (assoc modal :id modal-id)))))

(defmethod defacto/query-responder :toasts/?:toasts
  [db _]
  (->> (:toasts/toasts db)
       (sort-by key)
       (take 3)
       (map (fn [[toast-id toast]]
              (assoc toast :id toast-id)))))

(defmethod defacto/query-responder :toasts/?:toast
  [db [_ toast-id]]
  (some-> (get-in db [:toasts/toasts toast-id])
          (assoc :id toast-id)))
