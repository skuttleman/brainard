(ns brainard.common.views.main
  (:require
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.colls :as colls]
    [brainard.common.utils.maps :as maps]
    [clojure.pprint :as pp]))

(defn pprint [data]
  [:pre (with-out-str (pp/pprint data))])

(defn spinner
  ([]
   (spinner nil))
  ([{:keys [size]}]
   [(keyword (str "div.loader." (name (or size :small))))]))

(defn plain-button [{:keys [disabled] :as attrs} & content]
  (-> attrs
      (maps/assoc-defaults :type :button)
      (assoc :disabled disabled)
      (cond-> disabled (update :class (fnil conj []) "is-disabled"))
      (->> (conj [:button.button]))
      (into content)))

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
               listeners [(dom/add-listener! js/window :click (opener-click ref open?))
                          (dom/add-listener! js/window :keydown (opener-keydown open?) true)]
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

(defn with-resource [sub:res comp]
  (let [[_ opts :as comp] (colls/wrap-vector comp)
        [status data] @sub:res]
    (when (or (not= :init status) (not (:hide-init? opts)))
      (case status
        :success (conj comp data)
        :error [:div.error [alert :error "An error occurred."]]
        [spinner {:size (:spinner/size opts)}]))))
