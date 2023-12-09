(ns brainard.common.store.queries
  (:require
    [defacto.core :as defacto]))

(defmethod defacto/query-responder :app/?:loading
  [db _]
  (:app/loading? db false))

(defmethod defacto/query-responder :routing/?:route
  [db _]
  (:routing/info db))

(defmethod defacto/query-responder :resources/?:resource
  [db [_ handle]]
  (get-in db [:resources/resources handle] {:status :init}))

(defmethod defacto/query-responder :forms/?:form
  [db [_ form-id]]
  (get-in db [:forms/forms form-id]))

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
