(ns brainard.common.views.controls.shared
  (:require
    [defacto.forms.core :as forms]
    [defacto.resources.core :as res]))

(defn with-attrs
  "Prepares common form attributes used by controls in [[brainard.common.views.controls.core]]. "
  ([attrs form sub:res path]
   (with-attrs attrs (merge form @sub:res) path))
  ([attrs form+ path]
   (let [data (forms/data form+)
         payload (res/payload form+)]
     (assoc attrs
            :value (get-in data path)
            :changed? (forms/changed? form+ path)
            :warnings (when (and (res/error? form+)
                                 (not (:local? (meta payload)))
                                 (not (::forms/errors payload)))
                        (get-in payload path))
            :errors (when (and (not (res/init? form+))
                               (or (:local? (meta payload))
                                   (::forms/errors payload)))
                      (get-in (::forms/errors payload) path))
            :on-change [::forms/changed (forms/id form+) path]))))
