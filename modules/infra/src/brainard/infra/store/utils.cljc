(ns brainard.infra.store.utils)

(defn ^:private trim-modals [pred]
  (fn [modals]
    (filter pred modals)))

(defn only-modal-attrs
  "When only one modal exists, return attrs"
  [modals]
  (when (= 1 (count modals))
    (-> modals first :body second)))

(defn modals-of-state
  "Include only modals in a given state"
  [state]
  (trim-modals (comp #{state} :state)))

(defn modals-of-type
  "Include only modals of a given type"
  [type]
  (trim-modals (comp #{type} first :body)))

(defn modals-sans-state
  "Remove modals in a given state"
  [state]
  (trim-modals (complement (comp #{state} :state))))

(defn modals-sans-type
  "Remove modals of a given type"
  [type]
  (trim-modals (complement (comp #{type} first :body))))
