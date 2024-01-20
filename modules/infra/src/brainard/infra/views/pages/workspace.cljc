(ns brainard.infra.views.pages.workspace
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]))

(defmethod ipages/page :routes.ui/workspace
  [{:keys [*:store]}]
  (r/with-let [sub:data (do #?(:cljs (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#fetch]]))
                            (store/subscribe *:store [::res/?:resource [::specs/local ::specs/workspace#fetch]]))
               sub:form+ (do (store/dispatch! *:store [::forms/ensure! [::workspace]])
                             (store/subscribe *:store [::forms+/?:form+ [::workspace]]))]
    (let [form+ @sub:form+]
      [:div "welcome to the workspace"
       [ctrls/form {:*:store      *:store
                    :form+        form+
                    :resource-key [::specs/local ::specs/workspace#create!]}]])))
