(ns brainard.infra.views.fragments.note-components
  (:require
    [brainard.api.validations :as valid]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.notes.api.specs :as snotes]
    [defacto.forms.core :as forms]
    [defacto.resources.core :as res]
    [defacto.forms.plus :as forms+]
    [whet.utils.reagent :as r]))

(defn tag-list [note]
  (if-let [tags (not-empty (:notes/tags note))]
    [comp/tag-list {:value tags}]
    [:em "no tags"]))

(defn ^:private list-action [action-fn icon]
  [comp/plain-button {:class    ["is-small" "is-white"]
                      :style    {:padding 0 :height "2em"}
                      :on-click (fn [e]
                                  (dom/prevent-default! e)
                                  (action-fn))}
   icon])

(defn attachment-list [{:keys [label? on-edit on-remove value]}]
  [:div.layout--col
   (when label?
     [:label.label "Attachments:"])
   [:ul.attachment-list
    (for [{attachment-id :attachments/id :as attachment} (sort-by :attachments/id value)]
      ^{:key attachment-id}
      [:li.attachment.layout--room-between
       [comp/link {:token        :routes.resources/attachment
                   :route-params {:attachments/id attachment-id}
                   :target       "_blank"}
        (:attachments/name attachment)]
       (when on-remove
         [list-action #(on-remove attachment)
          [comp/icon {:class ["is-danger"]} :trash-can]])
       (when on-edit
         [list-action #(on-edit attachment)
          [comp/icon :pencil]])])]])

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
    [comp/icon {:class ["is-danger"]} :trash-can]]
   [list-action #(on-edit todo)
    [comp/icon :pencil]]])

(defmethod forms+/re-init ::notes#todo [_ form _] (forms/data form))
(defmethod res/->request-spec ::notes#todo
  [_ {::forms/keys [data] :as spec}]
  (let [spec (assoc spec :payload (-> data
                                      (select-keys #{:notes/todos})
                                      (valid/select-spec-keys snotes/full)))
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
  [:div.layout-col
   (when label?
     [:label.label "TODOs:"])
   (when on-create
     [comp/plain-button {:on-click #(on-create)}
      "Create TODO..."])
   [:ul.todo-list
    (for [{todo-id :todos/id :as todo} (sort-by (juxt (complement :todos/completed?) :todos/id) value)]
      ^{:key todo-id}
      [todo-item attrs todo])]])
