(ns brainard.infra.views.components.core
  "Reusable reagent components."
  (:require
    [brainard.api.utils.colls :as colls]
    [brainard.api.utils.maps :as maps]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.components.modals :as comp.modals]
    [brainard.infra.views.components.shared :as scomp]
    [brainard.infra.views.components.toasts :as comp.toasts]
    [clojure.pprint :as pp]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
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

(defn ^:private list-action [action-fn icon]
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
         [list-action #(on-remove attachment)
          [icon {:class ["is-danger"]} :trash-can]])
       (when on-edit
         [list-action #(on-edit attachment)
          [icon :pencil]])])]])

(defmulti ^:private ^{:attrs '([attrs todo])} todo-item
          (fn [{:keys [*:store]} _]
            (some? *:store)))

(defmethod todo-item false
  [{:keys [on-check on-edit on-remove]} todo]
  [:li.todo.layout--room-between
   [:input.checkbox
    {:checked   (boolean (:todos/completed? todo))
     :type      :checkbox
     :on-change #(on-check todo)}]
   [:span {:class [(when (:todos/completed? todo)
                     "strikethrough")]}
    (:todos/text todo)]
   [list-action #(on-remove todo)
    [icon {:class ["is-danger"]} :trash-can]]
   [list-action #(on-edit todo)
    [icon :pencil]]])

(defmethod forms+/re-init ::notes#todo [_ form _]
  (forms/data form))
(defmethod res/->request-spec ::notes#todo
  [_ {::forms/keys [data] :as spec}]
  (let [spec (assoc spec :payload (select-keys data #{:notes/todos}))
        note-id (:notes/id data)]
    (specs/with-cbs (res/->request-spec [::specs/notes#modify note-id] spec)
                    :ok-events [[:api.notes/saved]]
                    :ok-commands [[::res/submit! [::specs/notes#find note-id]]]
                    :err-commands [[:toasts/fail!]])))

(defmethod todo-item true
  [{:keys [*:store note-id]} todo]
  (r/with-let [init-form {:notes/id    note-id
                          :notes/todos [(select-keys todo #{:todos/id :todos/completed?})]}
               form-key [::forms+/std [::notes#todo (:todos/id todo)]]
               check-path [:notes/todos 0 :todos/completed?]
               sub:form+ (store/form+-sub *:store form-key init-form)]
    (let [form+ @sub:form+]
      [:li.todo.layout--room-between
       [:input.checkbox
        {:checked   (boolean (:todos/completed? todo))
         :type      :checkbox
         :value     (get-in (forms/data form+) check-path)
         :disabled  (res/requesting? form+)
         :on-change (fn [e]
                      (-> *:store
                          (store/emit! [::forms/changed
                                        form-key
                                        check-path
                                        (= "false" (dom/target-value e))])
                          (store/dispatch! [::forms+/submit!
                                            form-key
                                            {:ok-events  [[::res/swapped [::specs/notes#find note-id]]]
                                             :err-events [[::forms/created form-key init-form]]}])))}]

       [:span {:class [(when (:todos/completed? todo)
                         "strikethrough")]}
        (:todos/text todo)]])
    (finally
      (store/emit! *:store [::forms+/destroyed form-key]))))

(defn todo-list [{:keys [label? on-create value] :as attrs}]
  [:div.layout--stack-between
   [:div.layout-col
    (when label?
      [:em "TODOs:"])
    (when on-create
      [plain-button {:on-click #(on-create)}
       "Create TODO..."])]
   [:ul.todo-list
    (for [{todo-id :todos/id :as todo} (sort-by (juxt (complement :todos/completed?) :todos/id) value)]
      ^{:key todo-id}
      [todo-item attrs todo])]])

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
