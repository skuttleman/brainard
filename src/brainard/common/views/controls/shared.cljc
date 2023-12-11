(ns brainard.common.views.controls.shared
  (:require
    [brainard.common.forms.core :as forms]))

(defn with-attrs
  "Prepares common form attributes used by controls in [[brainard.common.views.controls.core]]. "
  [attrs form sub:res path]
  (let [data (forms/data form)
        {:keys [status payload]} @sub:res]
    (assoc attrs
           :value (get-in data path)
           :changed? (forms/changed? form path)
           :warnings (when (= :error status)
                       (get-in (:remote payload) path))
           :errors (when (not= :init status)
                     (get-in (:local payload) path))
           :on-change [:forms/changed (forms/id form) path])))
