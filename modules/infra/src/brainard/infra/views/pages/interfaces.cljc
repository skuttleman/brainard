(ns brainard.infra.views.pages.interfaces)

(defmulti ^{:arglists '([*:store route])} page
          "Implements a page from routing info."
          (fn [_ {:keys [token]}]
            token))

(defmulti ^{:arglists '([[modifier-set key-code] *:store])} kb-shortcut!
          "Handles a global keyboard shortcut"
          (fn [[modifier-set key-code] _]
            [modifier-set key-code]))
