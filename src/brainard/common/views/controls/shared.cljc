(ns brainard.common.views.controls.shared
  (:require
    [defacto.forms.core :as forms]))

(defn with-attrs
  "Prepares common form attributes used by controls in [[brainard.common.views.controls.core]]. "
  [attrs form sub:res path]
  (let [data (forms/data form)
        {:keys [status payload]} @sub:res]
    (assoc attrs
           :value (get-in data path)
           :changed? (forms/changed? form path)
           :warnings (when (and (= :error status) (not (:local? (meta payload))))
                       (get-in payload path))
           :errors (when (and (not= :init status) (:local? (meta payload)))
                     (get-in payload path))
           :on-change [::forms/changed (forms/id form) path])))
