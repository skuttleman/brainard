(ns brainard.common.forms
  (:require
    [brainard.common.utils.maps :as maps]))

(defn data [form]
  (when form
    (maps/nest (:form/current form))))

(defn change [form path value]
  (when form
    (if (and (nil? value) (-> form :form/opts :remove-nil?))
      (update form :form/current dissoc path)
      (assoc-in form [:form/current path] value))))

(defn create [id data opts]
  (let [current (maps/flatten data)]
    {:form/id      id
     :form/current current
     :form/opts    opts}))

(defn with-attrs
  ([attrs form sub:res path]
   (with-attrs attrs form sub:res path nil))
  ([attrs form sub:res path errors]
   (let [data (data form)
         [status result] @sub:res]
     (assoc attrs
            :value (get-in data path)
            :warnings (when (= :error status)
                        (get-in (:remote result) path))
            :errors (when (not= :init status)
                      (get-in errors path))
            :on-change [:forms/change (:form/id form) path]))))
