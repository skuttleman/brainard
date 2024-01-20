(ns brainard.infra.views.pages.workspace
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [defacto.forms.core :as-alias forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(defmethod ipages/page :routes.ui/workspace
  [{:keys [*:store]}]
  (r/with-let [sub:data (do #?(:cljs (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#fetch]]))
                            (store/subscribe *:store [::res/?:resource [::specs/local ::specs/workspace#fetch]]))
               sub:form+ (do (store/dispatch! *:store [::forms/ensure! [::forms+/std [::specs/local ::specs/workspace#create!]]])
                             (store/subscribe *:store [::forms+/?:form+ [::forms+/std [::specs/local ::specs/workspace#create!]]]))]
    (let [form+ @sub:form+]
      [:div "welcome to the workspace"
       [comp/pprint @sub:data]
       [ctrls/form {:*:store      *:store
                    :form+        form+
                    :resource-key [::forms+/std [::specs/local ::specs/workspace#create!]]}
        [ctrls/input (-> {:label   "Data"
                          :*:store *:store}
                         (ctrls/with-attrs form+ [:workspace-nodes/data]))]]])
    (finally
      (store/emit! *:store [::forms/destroyed [::forms+/std [::specs/local ::specs/workspace#create!]]]))))
