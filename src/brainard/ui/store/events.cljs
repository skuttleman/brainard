(ns brainard.ui.store.events
  (:require
    [brainard.common.forms :as forms]))

(defn create-form [db [_ id data validator]]
  (assoc-in db [::forms/form id] (forms/create id
                                               data
                                               (or validator (constantly nil)))))

(defn destroy-form [db [_ id]]
  (update db ::forms/form dissoc id))

(defn change-form [db [_ id path value]]
  (update-in db [::forms/form id]
             (fn [form]
               (-> form
                   (forms/change path value)
                   (forms/touch path)))))

(defn touch-form [db [_ id path :as action]]
  (let [form (get-in db [::forms/form id])]
    (if (= 2 (count action))
      (forms/touch form)
      (forms/touch form path))))
