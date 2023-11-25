(ns brainard.common.views.pages.search
  (:require
    [brainard.common.views.pages.common :as pages.common]))

(defmethod pages.common/page :routes.ui/search
  [_]
  [:div "search"])
