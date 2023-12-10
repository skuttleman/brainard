(ns brainard.common.views.pages.interfaces)

(defmulti ^{:arglists '([route])} page
          "Implements a page from routing info."
          :token)
