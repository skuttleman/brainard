(ns brainard.common.views.controls.shared
  (:require
    [defacto.forms.core :as forms]
    [defacto.resources.core :as res]))

(defn with-attrs
  "Prepares common form attributes used by controls in [[brainard.common.views.controls.core]]. "
  [attrs form sub:res path]
  (let [data (forms/data form)
        resource @sub:res
        payload (res/payload resource)]
    (assoc attrs
           :value (get-in data path)
           :changed? (forms/changed? form path)
           :warnings (when (and (res/error? resource)
                                (not (:local? (meta payload)))
                                (not (::forms/errors payload)))
                       (get-in payload path))
           :errors (when (and (not (res/init? resource))
                              (or (:local? (meta payload))
                                  (::forms/errors payload)))
                     (get-in (::forms/errors payload) path))
           :on-change [::forms/changed (forms/id form) path])))
