(ns brainard.common.views.components.core
  "Reusable reagent components."
  (:require
    [brainard.common.store.core :as store]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.colls :as colls]
    [brainard.common.utils.maps :as maps]
    [brainard.common.views.components.interfaces :as icomp]
    [brainard.common.views.components.modals :as comp.modals]
    [brainard.common.views.components.shared :as scomp]
    [brainard.common.views.components.toasts :as comp.toasts]
    [clojure.pprint :as pp]
    [defacto.forms.core :as forms]
    [defacto.resources.core :as res]))

(defn pprint [data]
  [:pre (with-out-str (pp/pprint data))])

(def ^{:arglists '([attrs & content])} plain-button
  scomp/plain-button)

(def ^{:arglists '([heading body & tabs])} tile
  scomp/tile)

(def ^{:arglists '([icon-class] [attrs icon-class])} icon
  scomp/icon)

(defn spinner
  ([]
   (spinner nil))
  ([{:keys [size]}]
   [(keyword (str "div.loader." (name (or size :small))))]))

(defn plain-input [attrs]
  [:input.input
   (-> attrs
       (select-keys #{:type :on-change :class :disabled :ref
                      :id :on-blur :value :on-focus :auto-focus})
       (update :on-change comp dom/target-value)
       (maps/assoc-defaults :type :text))])

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

(defn with-resources [sub:resources comp]
  (let [[_ opts :as comp] (colls/wrap-vector comp)
        [success? data-or-res] (loop [[sub:res :as subs] sub:resources
                                      successes []]
                                 (let [resource (some-> sub:res deref)]
                                   (cond
                                     (empty? subs) [true successes]
                                     (res/success? resource) (recur (next subs) (conj successes (res/payload resource)))
                                     :else [false resource])))]
    (when (or success?
              (not (:hide-init? opts))
              (not (res/init? data-or-res)))
      (cond
        success? (conj comp data-or-res)
        (res/error? data-or-res) (when-not (let [payload (res/payload data-or-res)]
                                             (or (:local? (meta payload))
                                                 (::forms/errors payload)))
                                   [:div.error [alert :error "An error occurred."]])
        :else [spinner opts]))))

(defn tag-list [{:keys [on-change value]}]
  [:div.tag-list.field.is-grouped.is-grouped-multiline.layout--space-between
   (for [tag value]
     ^{:key tag}
     [:div.tags.has-addons
      [:span.tag.is-info.is-light (str tag)]
      (when on-change
        [:button.button.tag.is-delete {:tab-index -1
                                       :on-click  (fn [e]
                                                    (dom/prevent-default! e)
                                                    (on-change (disj value tag)))}])])])

(def ^{:arglists '([*:store])} modals comp.modals/root)

(def ^{:arglists '([*:store])} toasts comp.toasts/root)

(defmethod icomp/modal-header :modals/sure?
  [_ _]
  "Are you sure?")

(defmethod icomp/modal-body :modals/sure?
  [*:store {:keys [description no-commands close! yes-commands]}]
  [:div.layout--stack-between
   [:p description]
   [:div.layout--room-between
    [plain-button {:class    ["is-info"]
                   :on-click (fn [e]
                               (run! (partial store/dispatch! *:store) yes-commands)
                               (close! e))}
     "OK"]
    [plain-button {:on-click (fn [e]
                               (run! (partial store/dispatch! *:store) no-commands)
                               (close! e))}
     "Cancel"]]])
