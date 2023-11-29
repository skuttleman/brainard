(ns brainard.common.forms
  (:require
    [brainard.common.utils.maps :as maps]))

(def ^:private validate
  (memoize (fn [validator data]
             (validator data))))

(defn data [form]
  (when form
    (maps/nest (:form/current form))))

(defn change [form path value]
  (when form
    (assoc-in form [:form/current path] value)))

(defn create [id data]
  (let [current (maps/flatten data)]
    {:form/id      id
     :form/init    current
     :form/current current}))

(defn with-attrs
  ([attrs form sub:res path]
   (with-attrs attrs form sub:res path (constantly nil)))
  ([attrs form sub:res path validator]
   (let [data (data form)
         errors (validate validator data)
         [status result] @sub:res]
     (assoc attrs
            :value (get-in data path)
            :warnings (when (= :error status)
                        (get-in (:remote result) path))
            :errors (when (not= :init status)
                      (get-in errors path))
            :on-change [:forms/change (:form/id form) path]))))
