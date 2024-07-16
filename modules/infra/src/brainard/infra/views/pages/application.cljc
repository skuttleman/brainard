(ns brainard.infra.views.pages.application
  (:require [brainard.infra.views.pages.interfaces :as ipages]))

(defmethod ipages/page :routes.ui/application
  [*:store {:keys [route-params]}]
  [:div "application view"])