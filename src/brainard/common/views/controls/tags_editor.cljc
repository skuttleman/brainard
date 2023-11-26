(ns brainard.common.views.controls.tags-editor
  (:require
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.controls.type-ahead :as type-ahead]
    [clojure.string :as string]))

(defn ^:private kw-str [kw]
  (if-let [[ns name] (when (keyword? kw)
                    [(namespace kw) (name kw)])]
    (cond->> (str name) ns (str ns "/"))
    (str kw)))

(def ^:private kw-re #"[a-z]([a-z0-9-]\.?)*(/[a-z][a-z0-9-]*)?")

(defn control [{:keys [on-change value tags] :as attrs}]
  (r/with-let [comp:state (r/atom {:value nil})]
    (letfn [(add-tag [e]
              (dom/prevent-default! e)
              (when-let [input-val (some-> (:value @comp:state) string/trim not-empty)]
                (when (re-matches kw-re input-val)
                  (on-change (conj value (keyword input-val)))
                  (swap! comp:state assoc :value nil))))]
      [:div.tags-editor
       [:div.field.has-addons
        [type-ahead/control (assoc attrs
                                   :placeholder "Add tag..."
                                   :value (:value @comp:state)
                                   :on-key-press (fn [e]
                                                     (when (#{:key-codes/enter} (dom/event->key e))
                                                       (dom/stop-propagation! e)
                                                       (add-tag e)))
                                   :on-change (comp (partial swap! comp:state assoc :value)
                                                    kw-str))]
        [:button.button.is-link {:on-click add-tag}
         "+"]]
       [:div.field.is-grouped.is-grouped-multiline.layout--space-between
        (for [tag value]
          ^{:key tag}
          [:div.tags.has-addons
           [:span.tag.is-info.is-light (str tag)]
           [:a.tag.is-delete.link {:href     "#"
                                   :on-click (fn [e]
                                               (dom/prevent-default! e)
                                               (on-change (disj value tag)))}]])]])))
