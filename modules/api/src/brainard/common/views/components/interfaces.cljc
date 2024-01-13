(ns brainard.common.views.components.interfaces)

(defmulti ^{:arglists '([*:store attrs])} modal-header
          "define the header of a modal"
          (fn [_store {:modals/keys [type]}]
            type))

(defmethod modal-header :default
  [_ _]
  nil)

(defmulti ^{:arglists '([*:store attrs])} modal-body
          "define the body of a modal"
          (fn [_store {:modals/keys [type]}]
            type))
