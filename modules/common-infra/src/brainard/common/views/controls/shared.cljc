(ns brainard.common.views.controls.shared
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.store.core :as store]))

(defn with-attrs
  "Prepares common form attributes used by controls in [[brainard.common.views.controls.core]]. "
  ([attrs form sub:res path]
   (with-attrs attrs form sub:res path nil))
  ([attrs form sub:res path errors]
   (let [data (forms/data form)
         [status result] @sub:res]
     (assoc attrs
            :value (get-in data path)
            :warnings (when (= :error status)
                        (get-in (:remote result) path))
            :errors (when (not= :init status)
                      (get-in errors path))
            :on-change [::store/emit! [:forms/changed (forms/id form) path]]))))
