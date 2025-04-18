(ns brainard.infra.views.fragments.note-edit
  (:require
    [brainard.api.utils.fns :as fns]
    [brainard.api.utils.uuids :as uuids]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.controls.core :as ctrls]
    [clojure.string :as string]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]
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
      [ctrls/type-ahead (-> {:*:store     *:store
                             :label       "Topic"
                             :sub:items   sub:contexts
                             :on-blur     on-context-blur
                             :auto-focus? (nil? (:notes/context form-data))}
                            (ctrls/with-attrs form+ [:notes/context])
                            (with-trim-on-blur *:store))]]
     [ctrls/icon-toggle (-> {:*:store *:store
                             :label   "Pin"
                             :icon    :paperclip}
                            (ctrls/with-attrs form+ [:notes/pinned?]))]]))

(defn ^:private body-field [{:keys [*:store form+]}]
  (let [form-data (forms/data form+)]
    [:<>
     [:label.label "Body"]
     [:div {:style {:margin-top 0}}
      (if (::preview? form-data)
        [:div.expanded
         [comp/markdown (:notes/body form-data)]]
        [ctrls/textarea (-> {:style       {:font-family :monospace
                                           :min-height  "250px"}
                             :*:store     *:store
                             :auto-focus? (some? (:notes/context form-data))}
                            (ctrls/with-attrs form+ [:notes/body]))])]]))

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
                                      :success "green"
                                      "blue")
                  :height           height
                  :width            (str (* 100 percent) "%")}}]])]))

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
  (fn [attachment]
    (letfn [(mapper [attachment' new-name]
              (cond-> attachment'
                (= (:attachments/id attachment')
                   (:attachments/id attachment))
                (assoc :attachments/name new-name)))]
      (store/dispatch! *:store
                       [:modals/create!
                        [::attachment-name
                         {:init      (select-keys attachment #{:attachments/id
                                                               :attachments/name})
                          :ok-events [[::forms/modified form-id [:notes/attachments] fns/smap mapper]]}]]))))

(defn ^:private ->on-remove-att [*:store form-id]
  (fn [{attachment-id :attachments/id}]
    (store/emit! *:store
                 [::forms/modified
                  form-id
                  [:notes/attachments]
                  (partial into #{} (remove (comp #{attachment-id} :attachments/id)))])))

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

(defn ^:private tags+todos+attachments-field [{:keys [*:store form+ sub:tags uploading?]}]
  (r/with-let [form-id (forms/id form+)
               on-upload-att (->on-upload-att *:store form-id)
               on-edit-att (->on-edit-att *:store form-id)
               on-remove-att (->on-remove-att *:store form-id)
               on-create-todo (->on-create-todo *:store form-id)
               on-check-todo (->on-check-todo *:store form-id)
               on-edit-todo (->on-edit-todo *:store form-id)
               on-remove-todo (->on-remove-todo *:store form-id)]
    (let [form-data (forms/data form+)]
      [:div.layout--room-between
       [:div {:style {:flex-basis "33%"}}
        [ctrls/tags-editor (-> {:*:store   *:store
                                :form-id   [::tags form-id]
                                :label     "Tags"
                                :sub:items sub:tags}
                               (ctrls/with-attrs form+ [:notes/tags]))]]
       [:div.layout--stack-between {:style {:flex-basis "33%"}}
        [:div.layout-col
         [:label.label "TODOs"]
         [comp/todo-list
          {:on-create on-create-todo
           :on-check  on-check-todo
           :on-edit   on-edit-todo
           :on-remove on-remove-todo
           :value     (:notes/todos form-data)}]]]
       [:div.layout-col {:style {:flex-basis "33%"}}
        [ctrls/file {:on-upload on-upload-att
                     :disabled  uploading?
                     :label     "Attachments"
                     :multi?    true}]
        [progress-bar (:upload-status form-data)]
        [comp/attachment-list
         {:on-edit   on-edit-att
          :on-remove on-remove-att
          :value     (:notes/attachments form-data)}]]])))

(defn ^:private note-form [{:keys [*:store form+] :as attrs}]
  (r/with-let [sub:uploads (store/subscribe *:store [::res/?:resource [::specs/attachment#upload]])]
    (let [uploading? (res/requesting? @sub:uploads)]
      [ctrls/form (assoc attrs :disabled uploading?)
       [topic+pin-field attrs]
       [body-field attrs]
       [ctrls/toggle (-> {:label   [:span.is-small "Preview"]
                          :style   {:margin-top 0}
                          :inline? true
                          :*:store *:store}
                         (ctrls/with-attrs form+ [::preview?]))]
       [tags+todos+attachments-field (assoc attrs :uploading? uploading?)]])
    (finally
      (store/emit! *:store [::res/destroyed [::specs/attachment#upload]]))))

(defmethod icomp/modal-header ::modal
  [_ {:keys [header]}]
  header)

(defmethod icomp/modal-body ::modal
  [*:store {modal-id :modals/id :modals/keys [close!] :keys [init params resource-key]}]
  (r/with-let [sub:form+ (store/form+-sub *:store resource-key init)
               sub:contexts (store/res-sub *:store [::specs/contexts#select])
               sub:tags (store/res-sub *:store [::specs/tags#select])
               params (update params :ok-commands conj [:modals/remove! modal-id])]
    [:div {:style {:min-width "50vw"}}
     [note-form {:*:store      *:store
                 :form+        @sub:form+
                 :params       params
                 :resource-key resource-key
                 :sub:contexts sub:contexts
                 :sub:tags     sub:tags
                 :submit/body  "Save"
                 :buttons      [[comp/plain-button {:on-click close!}
                                 "Cancel"]]}]]
    (finally
      (store/emit! *:store [::forms+/destroyed resource-key]))))

(defmethod icomp/modal-header ::attachment-name
  [_ _]
  "Edit attachment name")

(defmethod icomp/modal-body ::attachment-name
  [*:store {:modals/keys [close!] :keys [init ok-events]}]
  (r/with-let [sub:form (store/form-sub *:store [::attachment-edit!] init)]
    (let [form @sub:form]
      [ctrls/plain-form
       {:on-submit   (fn [_]
                       (close!)
                       (run! (fn [event]
                               (store/emit! *:store (conj event (:attachments/name (forms/data form)))))
                             ok-events))
        :submit/body "Update"}
       [ctrls/input (-> {:*:store     *:store
                         :auto-focus? true
                         :label       "Attachment name"}
                        (ctrls/with-attrs form [:attachments/name]))]])
    (finally
      (store/emit! *:store [::forms/destroyed [::attachment-edit!]]))))

(defmethod icomp/modal-header ::todo
  [_ {:keys [new?]}]
  (if new?
    "Create new TODO"
    "Edit your TODO"))

(defmethod icomp/modal-body ::todo
  [*:store {:modals/keys [close!] :keys [new? init ok-events]}]
  (r/with-let [sub:form (store/form-sub *:store [::todo-edit] init)]
    (let [form @sub:form]
      [ctrls/plain-form
       {:on-submit   (fn [_]
                       (close!)
                       (run! (fn [event]
                               (store/emit! *:store (conj event (forms/data form))))
                             ok-events))
        :submit/body (if new? "Create" "Update")}
       [ctrls/input (-> {:*:store     *:store
                         :auto-focus? true
                         :label       "TODO"}
                        (ctrls/with-attrs form [:todos/text]))]])
    (finally
      (store/emit! *:store [::forms/destroyed [::todo-edit]]))))
