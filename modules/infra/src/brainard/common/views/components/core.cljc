(ns brainard.common.views.components.core
  "Reusable reagent components."
  (:require
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.colls :as colls]
    [brainard.common.utils.maps :as maps]
    [brainard.common.views.components.toasts :as comp.toasts]
    [clojure.pprint :as pp]))

(defn pprint [data]
  [:pre (with-out-str (pp/pprint data))])

(defn spinner
  ([]
   (spinner nil))
  ([{:keys [size]}]
   [(keyword (str "div.loader." (name (or size :small))))]))

(defn plain-button [attrs & content]
  (let [disabled #?(:clj true :default (:disabled attrs))]
    (-> attrs
        (maps/assoc-defaults :type :button)
        (cond-> disabled (update :class (fnil conj []) "is-disabled"))
        (->> (conj [:button.button]))
        (into content))))

(defn plain-input [attrs]
  [:input.input
   (-> attrs
       (select-keys #{:type :on-change :class :disabled :ref
                      :id :on-blur :value :on-focus :auto-focus})
       (update :on-change comp dom/target-value)
       (maps/assoc-defaults :type :text))])

(defn icon
  ([icon-class]
   (icon {} icon-class))
  ([attrs icon-class]
   [:i.fas (update attrs :class conj (str "fa-" (name icon-class)))]))

(def ^:private level->class
  {:error "is-danger"})

(defn alert [level body]
  [:div.message
   {:class [(level->class level)]}
   [:div.message-body
    body]])

(defn ^:private within-ref? [target node]
  (->> target
       (iterate #(some-> % .-parentNode))
       (take-while some?)
       (filter #{node})
       seq
       boolean))

(defn ^:private opener-click [ref open?]
  (fn [e]
    (if (within-ref? (.-target e) @ref)
      (dom/focus! @ref)
      (do (reset! open? false)
          (dom/blur! @ref)))))

(defn ^:private opener-keydown [open?]
  (fn [e]
    (case (dom/event->key e)
      (:key-codes/tab :key-codes/esc) (reset! open? false)
      nil)))

(defn openable [component & args]
  (r/with-let [open? (r/atom false)
               ref (volatile! nil)
               listeners [(dom/add-listener! dom/window :click (opener-click ref open?))
                          (dom/add-listener! dom/window :keydown (opener-keydown open?) true)]
               on-toggle (fn [_]
                           (swap! open? not))
               ref-fn (fn [node]
                        (some->> node (vreset! ref)))
               on-blur (fn [_]
                         (when-let [node @ref]
                           (when @open?
                             (dom/focus! node))))]
    (into [component {:open?     @open?
                      :on-toggle on-toggle
                      :ref       ref-fn
                      :on-blur   on-blur}]
          args)
    (finally
      (run! dom/remove-listener! listeners))))

(defn with-resources [resources comp]
  (let [[_ opts :as comp] (colls/wrap-vector comp)
        [status data] (loop [[sub:res :as resources] resources
                             successes []]
                        (let [[status data] (some-> sub:res deref)]
                          (cond
                            (empty? resources) [:success successes]
                            (= :success status) (recur (next resources) (conj successes data))
                            :else [status data])))]
    (when (or (not= :init status) (not (:hide-init? opts)))
      (case status
        :success (conj comp data)
        :error (when-not (:local data)
                 [:div.error [alert :error "An error occurred."]])
        [spinner opts]))))

(defn tag-list [{:keys [on-change value]}]
  [:div.tag-list.field.is-grouped.is-grouped-multiline.layout--space-between
   (for [tag value]
     ^{:key tag}
     [:div.tags.has-addons
      [:span.tag.is-info.is-light (str tag)]
      (when on-change
        [:button.button.tag.is-delete {:on-click (fn [e]
                                                   (dom/prevent-default! e)
                                                   (on-change (disj value tag)))}])])])

(def ^{:arglists '([*:store])} toasts comp.toasts/toasts)
