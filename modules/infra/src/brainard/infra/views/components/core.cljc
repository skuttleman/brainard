(ns brainard.infra.views.components.core
  "Reusable reagent components."
  (:require
    [brainard.api.utils.colls :as colls]
    [brainard.api.utils.maps :as maps]
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.components.modals :as comp.modals]
    [brainard.infra.views.components.shared :as scomp]
    [brainard.infra.views.components.toasts :as comp.toasts]
    [clojure.pprint :as pp]
    [defacto.forms.core :as forms]
    [defacto.resources.core :as res]
    [nextjournal.markdown :as md]
    [nextjournal.markdown.transform :as md.trans]
    [whet.utils.reagent :as r]))

(defn pprint [data]
  [:pre (with-out-str (pp/pprint data))])

(def ^{:arglists '([attrs & content])} plain-button
  (scomp/with-auto-focus scomp/plain-button))

(def ^{:arglists '([attrs & content])} link
  scomp/link)

(def ^{:arglists '([heading body & tabs])} tile
  scomp/tile)

(def ^{:arglists '([icon-class] [attrs icon-class])} icon
  scomp/icon)

(defn spinner
  ([]
   (spinner nil))
  ([{:keys [size]}]
   [:div.loader {:class [(name (or size :small))]}]))

(defn plain-input [attrs]
  [:input.input
   (-> attrs
       (select-keys #{:auto-focus :class :disabled :id :on-blur :style
                      :on-change :on-click :on-focus :ref :type :value
                      :placeholder})
       (update :on-change comp dom/target-value)
       (maps/assoc-defaults :type :text))])

(def ^:private level->class
  {:warn  "is-warning"
   :error "is-danger"})

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

(defn openable [{:keys [listeners?]} component & args]
  (r/with-let [open? (r/atom false)
               ref (volatile! nil)
               listeners (when listeners?
                           [(dom/add-listener! dom/window :click (opener-click ref open?))
                            (dom/add-listener! dom/window :keydown (opener-keydown open?) true)])
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
        (res/error? data-or-res) (when-not (::forms/errors (res/payload data-or-res))
                                   [:div.error [alert :error "An error occurred."]])
        :else [spinner opts]))))

(defn ^:private single-resource [_opts comp [value]]
  (conj comp value))

(defn with-resource [sub:resource comp]
  (let [[_ opts :as comp] (colls/wrap-vector comp)]
    [with-resources [sub:resource] [single-resource opts comp]]))

(defn tag-list [{:keys [on-change value]}]
  [:div.tag-list.field.is-grouped.is-grouped-multiline.layout--space-between
   (for [tag value]
     ^{:key tag}
     [:div.tags.has-addons
      [:span.tag.is-info.is-light (str tag)]
      (when on-change
        [plain-button {:tab-index -1
                       :class     ["tag" "is-delete"]
                       :on-click  (fn [e]
                                    (dom/prevent-default! e)
                                    (on-change (disj value tag)))}])])])

(defn ^:private attachment-action [action-fn icon]
  [plain-button {:class    ["is-small" "is-white"]
                 :style    {:padding 0 :height "2em"}
                 :on-click (fn [e]
                             (dom/prevent-default! e)
                             (action-fn))}
   icon])

(defn attachment-list [{:keys [label? on-edit on-remove value]}]
  [:div.layout--stack-between
   (when label?
     [:em "Attachments:"])
   [:ul.attachment-list
    (for [{attachment-id :attachments/id :as attachment} (sort-by :attachments/id value)]
      ^{:key attachment-id}
      [:li.attachment.layout--room-between
       [link {:token        :routes.resources/attachment
              :route-params {:attachments/id attachment-id}
              :target       "_blank"}
        (:attachments/name attachment)]
       (when on-remove
         [attachment-action #(on-remove attachment)
          [icon {:class ["is-danger"]} :trash-can]])
       (when on-edit
         [attachment-action #(on-edit attachment)
          [icon :pencil]])])]])

(defn markdown [content]
  [:div.content
   (some-> content md/parse md.trans/->hiccup)])

(def ^{:arglists '([*:store])} modals comp.modals/root)

(def ^{:arglists '([*:store])} toasts comp.toasts/root)

(defmethod icomp/modal-header :modals/sure?
  [_ _]
  "Are you sure?")

(defmethod icomp/modal-body :modals/sure?
  [*:store {:modals/keys [close!] :keys [description no-commands yes-commands]}]
  [:div.layout--stack-between
   [:p description]
   [:div.layout--room-between
    [plain-button {:class       ["is-info"]
                   :auto-focus? true
                   :on-click    (fn [e]
                                  (run! (partial store/dispatch! *:store) yes-commands)
                                  (close! e))}
     "OK"]
    [plain-button {:on-click (fn [e]
                               (run! (partial store/dispatch! *:store) no-commands)
                               (close! e))}
     "Cancel"]]])
