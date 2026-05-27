(ns brainard.infra.views.controls.type-ahead
  "A type-ahead select component which allows new values."
  (:require
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [clojure.string :as string]))

(defn ^:private filter-matches [value items]
  (let [re (re-pattern (string/lower-case (str "(?i)" value)))]
    (filter (fn [item]
              (re-find re (str item)))
            items)))

(defn control* [{:keys [on-change value] :as attrs} items]
  (let [list-id (gensym "data-list")
        matches (filter-matches value items)]
    [:<>
     [:input.input (-> {:list          list-id
                        :type          :text
                        :auto-complete :off
                        :on-change     (comp on-change dom/target-value)}
                       (merge (select-keys attrs #{:class :disabled :id :ref :value :on-focus :on-blur :auto-focus})))]
     [:datalist {:id list-id}
      (for [match matches]
        ^{:key match} [:option {:value match}])]]))

(defn control [attrs]
  [comp/with-resource (:sub:items attrs) [control* attrs]])
