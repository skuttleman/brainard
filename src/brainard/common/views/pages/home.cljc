(ns brainard.common.views.pages.home
  (:require
    [brainard.common.views.pages.common :as pages.common]))

(defmethod pages.common/page :routes.ui/home
  [_]
  [:div "home"])
