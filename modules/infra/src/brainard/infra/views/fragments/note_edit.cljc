(ns brainard.infra.views.fragments.note-edit
  (:require
   [brainard.infra.store.core :as store]
   [brainard.infra.store.specs :as-alias specs]
   [brainard.infra.stubs.dom :as dom]
   [brainard.infra.views.components.core :as comp]
   [brainard.infra.views.components.interfaces :as icomp]
   [brainard.infra.views.controls.core :as ctrls]
   [brainard.infra.views.fragments.actions :as frag.act]
   [brainard.infra.views.fragments.note-components :as note-comp]
   [clojure.string :as string]
   [defacto.forms.core :as forms]
   [defacto.resources.core :as res]
   [slag.utils.fns :as fns]
   [slag.utils.uuids :as uuids]
   [whet.core :as-alias w]
   [whet.utils.reagent :as r]))

(defn ^:private with-trim-on-blur [{:keys [on-change] :as attrs} *:store]
  (update attrs :on-blur fns/safe-comp (fn [e]
                                         (let [v (dom/target-value e)
                                               trimmed-v (not-empty (string/trim v))]
                                           (when (not= trimmed-v v)
                                             (store/emit! *:store (conj on-change trimmed-v)))
                                           e))))

(defn ^:private topic+pin-field [{:keys [*:store form+ on-context-blur sub:contexts]}]
  (let [form-data (forms/data form+)]
    [:div.layout--space-between
     [:div.flex-grow
      [:div {:style {:margin-bottom "16px"}}
       [ctrls/autocomplete (-> {:*:store     *:store
                                :label       "Topic"
                                :sub:items   sub:contexts
                                :on-blur     on-context-blur
                                :auto-focus? (nil? (:notes/context form-data))}
                               (ctrls/with-attrs form+ [:notes/context])
                               (with-trim-on-blur *:store))]]]
     [ctrls/icon-toggle (-> {:*:store *:store
                             :label   "Pin"
                             :icon    :paperclip-1}
                            (ctrls/with-attrs form+ [:notes/pinned?]))]]))

(defn ^:private body-field [{:keys [*:store form+]}]
  (let [form-data (forms/data form+)]
    [:div {:style {:margin-top 0}}
     (if (::preview? form-data)
       [:<>
        [:p.label "Body"]
        [:div.expanded
         [comp/markdown (:notes/body form-data)]]]
       [ctrls/textarea (-> {:style       {:font-family :monospace
                                          :min-height  "250px"}
                            :label       "Body"
                            :*:store     *:store
                            :auto-focus? (some? (:notes/context form-data))}
                           (ctrls/with-attrs form+ [:notes/body]))])]))

(defn ^:private progress-bar [{:keys [loaded status total]}]
  (let [height "4px"
        complete? (#{:complete :error} status)
        percent (cond
                  (= status :init) 0.02
                  complete? 1
                  total (min (/ loaded total) 0.98))]
    [:div {:style {:height    height
                   :width     "400px"
                   :max-width "90%"
                   :outline   (when percent "1px grey solid")
                   :margin    "5px 0 3px 0"}}
     (when percent
       [:div.progress-bar
        {:style {:height height}}
        [:div.progress-amount
         {:class [(cond
                    (zero? percent) "unstarted"
                    complete? "complete")]
          :style {:background-color (case status
                                      :error "red"
                                      :complete "green"
                                      "blue")
                  :height           height
                  :width            (str (* 100 percent) "%")}}]])]))

(defn ^:private ->on-create-link [*:store form-id linked-ids]
  (fn []
    (store/dispatch! *:store
                     [:modals/create!
                      [::link
                       {:select-event [::forms/modified
                                       form-id
                                       [:notes/links]
                                       #(conj (vec %1) %2)]
                        :linked-ids   linked-ids
                        :style        {:overflow-y :visible}}]])))

(defn ^:private ->on-remove-link [*:store form-id]
  (fn [{link-id :notes/id}]
    (store/emit! *:store
                 [::forms/modified
                  form-id
                  [:notes/links]
                  (partial remove (comp #{link-id} :notes/id))])))

(defn ^:private note-links [*:store form-id note-id links]
  (r/with-let [on-remove-link (->on-remove-link *:store form-id)]
    (let [unlinkable (into #{note-id} (map :notes/id) links)]
      [note-comp/note-links
       {:label?    true
        :on-create (->on-create-link *:store form-id unlinkable)
        :on-remove on-remove-link
        :value     links}])))

(defn ^:private ->on-upload-att [*:store form-id]
  (fn [files]
    (when (seq files)
      (store/dispatch! *:store
                       [::res/submit!
                        [::specs/attachment#upload]
                        {:files        files
                         :pre-events   [[::forms/changed form-id [:upload-status] {:status :init}]]
                         :prog-events  [[::forms/changed form-id [:upload-status]]]
                         :ok-events    [[::forms/modified form-id [:notes/attachments] into]]
                         :err-commands [[:toasts/fail!]]
                         :err-events   [[::forms/changed form-id [:upload-status] {:status :error}]]}]))))

(defn ^:private ->on-edit-att [*:store form-id]
  (fn [{attachment-id :attachments/id :as init}]
    (letfn [(mapper [attachment' {:attachments/keys [name]}]
              (cond-> attachment'
                (= (:attachments/id attachment')
                   attachment-id)
                (assoc :attachments/name name)))]
      (store/dispatch! *:store
                       [:modals/create!
                        [::attachment-name
                         {:init      (select-keys init #{:attachments/id
                                                         :attachments/name})
                          :ok-events [[::forms/modified form-id [:notes/attachments] fns/smap mapper]]}]]))))

(defn ^:private ->on-remove-att [*:store form-id]
  (fn [{attachment-id :attachments/id}]
    (store/emit! *:store
                 [::forms/modified
                  form-id
                  [:notes/attachments]
                  (partial into #{} (remove (comp #{attachment-id} :attachments/id)))])))

(defn ^:private attachment-list [*:store form-id uploading? upload-status attachments]
  (r/with-let [on-upload-att (->on-upload-att *:store form-id)
               on-edit-att (->on-edit-att *:store form-id)
               on-remove-att (->on-remove-att *:store form-id)]
    [:<>
     [ctrls/file {:on-upload on-upload-att
                  :disabled  uploading?
                  :label     "Attachments"
                  :multi?    true}]
     [progress-bar upload-status]
     [note-comp/attachment-list
      {:on-edit   on-edit-att
       :on-remove on-remove-att
       :value     attachments}]]))

(defn ^:private ->on-create-todo [*:store form-id]
  (fn []
    (store/dispatch! *:store
                     [:modals/create!
                      [::todo
                       {:init      {:todos/id         (uuids/random)
                                    :todos/completed? false}
                        :new?      true
                        :ok-events [[::forms/modified
                                     form-id
                                     [:notes/todos]
                                     #(conj (vec %1) %2)]]}]])))

(defn ^:private ->on-edit-todo [*:store form-id]
  (fn [{todo-id :todos/id :as init}]
    (store/dispatch! *:store
                     [:modals/create!
                      [::todo
                       {:init      init
                        :ok-events [[::forms/modified
                                     form-id
                                     [:notes/todos]
                                     fns/smap
                                     #(if (= todo-id (:todos/id %1))
                                        %2
                                        %1)]]}]])))

(defn ^:private ->on-check-todo [*:store form-id]
  (fn [{todo-id :todos/id}]
    (store/emit! *:store
                 [::forms/modified
                  form-id
                  [:notes/todos]
                  fns/smap
                  #(cond-> %
                     (= (:todos/id %) todo-id)
                     (update :todos/completed? not))])))

(defn ^:private ->on-remove-todo [*:store form-id]
  (fn [{todo-id :todos/id}]
    (store/emit! *:store
                 [::forms/modified
                  form-id
                  [:notes/todos]
                  (partial remove (comp #{todo-id} :todos/id))])))

(defn ^:private todo-list [*:store form-id todos]
  (r/with-let [on-create-todo (->on-create-todo *:store form-id)
               on-check-todo (->on-check-todo *:store form-id)
               on-edit-todo (->on-edit-todo *:store form-id)
               on-remove-todo (->on-remove-todo *:store form-id)]
    [note-comp/todo-list
     {:label?    true
      :on-create on-create-todo
      :on-check  on-check-todo
      :on-edit   on-edit-todo
      :on-remove on-remove-todo
      :value     todos}]))

(defn ^:private addendums [{:keys [*:store form+ sub:tags uploading?]}]
  (r/with-let [form-id (forms/id form+)]
    (let [form-data (forms/data form+)]
      [:div.layout--room-between
       [:div {:style {:flex-basis "25%"}}
        [ctrls/tags-editor (-> {:*:store   *:store
                                :form-id   [::tags form-id]
                                :label     "Tags"
                                :sub:items sub:tags}
                               (ctrls/with-attrs form+ [:notes/tags]))]]
       [:div {:style {:flex-basis "25%"}}
        [note-links
         *:store
         form-id
         (:notes/id form-data)
         (:notes/links form-data)]]
       [:div {:style {:flex-basis "25%"}}
        [todo-list *:store form-id (:notes/todos form-data)]]
       [:div.layout-col {:style {:flex-basis "25%"}}
        [attachment-list
         *:store
         form-id
         uploading?
         (:upload-status form-data)
         (:notes/attachments form-data)]]])))

(defn ^:private note-form [{:keys [*:store form+] :as attrs}]
  (store/with-let [sub:uploads (store/subscribe *:store [::res/?:resource [::specs/attachment#upload]])]
    (let [uploading? (res/requesting? @sub:uploads)]
      [ctrls/form (assoc attrs :disabled uploading?)
       [topic+pin-field attrs]
       [body-field attrs]
       [ctrls/toggle (-> {:label   [:span.is-small "Preview"]
                          :style   {:margin-top 0}
                          :inline? true
                          :*:store *:store}
                         (ctrls/with-attrs form+ [::preview?]))]
       [addendums (assoc attrs :uploading? uploading?)]])
    (finally
      (store/emit! *:store [::res/destroyed [::specs/attachment#upload]]))))

(defmethod icomp/modal-header ::modal
  [_ {:keys [header]}]
  [:h1.note__modal-header header])

(defmethod icomp/modal-body ::modal
  [*:store {modal-id :modals/id :modals/keys [close!] :keys [init params resource-key]}]
  (store/with-let [sub:form+ (store/form+-sub *:store resource-key init)
                   sub:contexts (store/res-sub *:store ^:static [::specs/contexts#select])
                   sub:tags (store/res-sub *:store ^:static [::specs/tags#select])
                   params (update params :ok-commands conj [:modals/remove! modal-id])]
    [:div {:style {:min-width "50vw"}}
     [note-form {:*:store      *:store
                 :form+        @sub:form+
                 :params       params
                 :resource-key resource-key
                 :sub:contexts sub:contexts
                 :sub:tags     sub:tags
                 :submit/body  "Save"
                 :buttons      [[comp/plain-button {:class    ["cancel"]
                                                    :on-click close!}
                                 "Cancel"]]}]]))

(defmethod icomp/modal-header ::attachment-name
  [_ _]
  "Edit attachment name")

(defmethod icomp/modal-header ::todo
  [_ {:keys [new?]}]
  (if new? "Create new TODO" "Edit your TODO"))

(defn ^:private form-modal [*:store {modal-id :modals/id :keys [init resource-key] :as attrs}]
  (store/with-let [sub:form+ (store/form+-sub *:store resource-key init)]
    (let [{:keys [form-path label ok-events resource-key submit-body]} attrs
          form+ @sub:form+]
      [ctrls/form
       {:*:store      *:store
        :disabled     (and (res/error? form+) (not (forms/changed? form+ form-path)))
        :form+        form+
        :params       {:ok-commands [[:modals/remove! modal-id]]
                       :ok-events   ok-events}
        :resource-key resource-key
        :submit/body  submit-body}
       [ctrls/input (-> {:*:store     *:store
                         :auto-focus? true
                         :label       label}
                        (ctrls/with-attrs form+ form-path))]])))

(defmethod icomp/modal-body ::attachment-name
  [*:store attrs]
  [form-modal *:store (assoc attrs
                             :form-path [:attachments/name]
                             :label "Attachment name"
                             :resource-key frag.act/attachment-form-key
                             :submit-body "Update")])

(defmethod icomp/modal-body ::todo
  [*:store {:keys [new?] :as attrs}]
  [form-modal *:store (assoc attrs
                             :form-path [:todos/text]
                             :label "TODO"
                             :resource-key frag.act/todo-form-key
                             :submit-body (if new? "Create" "Update"))])

(defmethod icomp/modal-header ::link
  [_ _]
  "Link another note to this one")

(defmethod icomp/modal-body ::link
  [*:store {modal-id :modals/id :keys [linked-ids select-event]}]
  (store/with-let [sub:matches (store/res-init-sub *:store frag.act/link-search-key [])
                   sub:form (store/form-sub *:store [::link-form] nil)
                   on-select (fn [note]
                               (-> *:store
                                   (store/emit! (conj select-event note))
                                   (store/dispatch! [:modals/remove! modal-id])))
                   remove-fn (comp linked-ids :notes/id)]
    (let [form @sub:form]
      [:div {:style {:min-height "100px"}}
       [ctrls/typeahead (-> {:*:store    *:store
                             :auto-focus true
                             :label      "Search for a note"
                             :item-fn    :notes/summary
                             :key-fn     :notes/id
                             :on-select  on-select
                             :remove-fn  remove-fn
                             :sub:items  sub:matches}
                            (ctrls/with-attrs form [::link-search])
                            (update :on-change
                                    (fn [event]
                                      {:command [::res/debounce!
                                                 ::search
                                                 400
                                                 [::res/resubmit! frag.act/link-search-key]]
                                       :event   event})))]])))
