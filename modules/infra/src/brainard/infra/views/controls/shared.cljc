(ns brainard.infra.views.controls.shared
  (:require
   [defacto.forms.core :as forms]
   [defacto.resources.core :as res]))

(defn with-attrs
  "Prepares common form attributes used by controls in [[brainard.infra.views.controls.core]]. "
  [attrs form+ path]
  (let [data (forms/data form+)
        errors (res/errors form+)]
    (assoc attrs
           :value (get-in data path)
           :changed? (forms/changed? form+ path)
           :warnings (when (and (res/error? form+)
                                (not (::forms/errors errors)))
                       (if (sequential? errors)
                         (->> errors
                              (mapcat (comp #(get-in % path) :details))
                              seq)
                         (get-in errors path)))
           :errors (when (and (not (res/init? form+))
                              (::forms/errors errors))
                     (get-in (::forms/errors errors) path))
           :on-change [::forms/changed (forms/id form+) path])))
