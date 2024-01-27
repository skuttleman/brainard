(ns brainard.infra.views.pages.interfaces)

(defmulti ^{:arglists '([*:store route])} page
          "Implements a page from routing info."
          (fn [_ {:keys [token]}]
            token))
