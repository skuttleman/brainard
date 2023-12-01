(ns brainard.common.forms
  (:require
    [brainard.common.utils.maps :as maps]))

(defn data [form]
  (when form
    (maps/nest (::current form))))

(defn change [form path value]
  (when form
    (if (and (nil? value) (-> form ::opts :remove-nil?))
      (update form ::current dissoc path)
      (assoc-in form [::current path] value))))

(defn changed? [{::keys [current init]}]
  (not= current init))

(defn create [id data opts]
  (let [internal-data (maps/flatten data)]
    {::id      id
     ::init    internal-data
     ::current internal-data
     ::opts    opts}))

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
            :on-change [:forms/change (::id form) path]))))
