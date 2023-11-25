(ns brainard.common.views.pages.common)

(defmulti page "Define a root page" :handler)

(defmethod page :default
  [_]
  [:div "not found"])
