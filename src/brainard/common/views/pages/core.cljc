(ns brainard.common.views.pages.core
  (:require
    [brainard.common.views.pages.common :as pages.common]
    brainard.common.views.pages.search
    brainard.common.views.pages.home))

(defn page [route]
  (pages.common/page route))
