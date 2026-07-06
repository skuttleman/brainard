(ns brainard.infra.views.pages.interfaces)

(defmulti ^{:arglists '([*:store route])} page
          "Implements a page from routing info."
          (fn [_ {:keys [token]}]
            token))

(defmulti ^{:arglists '([*:store [modifier-set key-code]])} kb-shortcut!
          "Handles a global keyboard shortcut"
          (fn [_ [modifier-set key-code]]
            [modifier-set key-code]))
