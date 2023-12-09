(ns brainard.common.views.pages.interfaces)

(defmulti page
          "Implements a page from routing info."
          :token)
