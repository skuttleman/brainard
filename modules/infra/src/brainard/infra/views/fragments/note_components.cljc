(ns brainard.infra.views.fragments.note-components
  (:require
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.controls.core :as ctrls]))

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

(defmulti ^{:attrs '([attrs todo])} todo-item
          (fn [{:keys [*:store]} _]
            (some? *:store)))

(defmethod todo-item false
  [{:keys [disabled on-check on-edit on-remove]} todo]
  [:li.todo.layout--room-between
   [ctrls/toggle {:disabled  disabled
                  :value     (boolean (:todos/completed? todo))
                  :on-change #(on-check todo)}]
   [:span {:class [(when (:todos/completed? todo)
                     "strikethrough")]}
    (:todos/text todo)]
   (when on-remove
     [list-action #(on-remove todo)
      [comp/icon {:class ["is-danger"]} :trash-can]])
   (when on-edit
     [list-action #(on-edit todo)
      [comp/icon :pencil]])])

(defn todo-list [{:keys [disabled label? on-create value] :as attrs}]
  [:div.layout-col
   (when label?
     [:label.label "TODOs:"])
   (when on-create
     [comp/plain-button {:on-click #(on-create)
                         :class ["note__create-todo-button"]}
      "Create TODO..."])
   [:ul.todo-list
    (for [{todo-id :todos/id :as todo} (sort-by (juxt (complement :todos/completed?) :todos/id) value)]
      ^{:key (str todo-id "-" disabled)}
      [todo-item attrs todo])]])
