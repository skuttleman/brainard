(ns brainard.infra.views.pages.home.actions
  (:require
    [brainard.api.validations :as valid]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.fragments.note-edit :as note-edit]
    [brainard.notes.api.specs :as snotes]
    [brainard.workspace.api.specs :as sws]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]
    [workspace-nodes :as-alias ws]))

(def ^:const create-note-key [::forms+/valid [::notes#create]])
(def ^:const ws-form-key [::forms+/valid [::workspace#sync]])

(defmulti drag-item (fn [_ attrs _] (:type attrs)))

(forms+/validated ::notes#create (valid/->validator snotes/create)
  [_ {::forms/keys [data] :as spec}]
  (let [spec (assoc spec :payload (valid/select-spec-keys data snotes/create))]
    (specs/with-cbs (res/->request-spec [::specs/notes#create] spec)
                    :ok-events [[:api.notes/saved]]
                    :ok-commands [[:toasts.notes/succeed!]]
                    :err-commands [[:toasts/fail!]])))

(defmethod res/->request-spec ::notes#pinned
  [_ spec]
  (res/->request-spec [::specs/notes#select] (assoc spec :params {:pinned true})))

(def ^:no-doc workspace-validator
  (let [create (valid/->validator sws/create)
        modify (valid/->validator sws/modify)]
    (fn [data]
      (if (::ws/id data)
        (modify data)
        (create data)))))

(forms+/validated ::workspace#sync workspace-validator
  [_ {::forms/keys [data] :keys [payload] :as spec}]
  (let [spec-key (case (::action spec)
                   ::create ::specs/workspace#create
                   ::modify ::specs/workspace#modify
                   ::destroy ::specs/workspace#destroy
                   ::specs/workspace#select)]
    (res/->request-spec [spec-key]
                        (assoc spec
                               ::ws/id (or (::ws/id data) (::ws/id spec))
                               :payload (or data payload)
                               :ok-commands [[:modals/remove-all!]]
                               :err-commands [[:toasts/fail!]]))))

(defn expanded-change
  "Return a listener that updates the route query-params when the expanded node changes."
  [*:store route-info]
  (fn [_ _ old new]
    (let [old-expanded (::expanded (forms/data old))
          new-expanded (::expanded (forms/data new))
          next-route (when (not= old-expanded new-expanded)
                       (assoc route-info :query-params (if new-expanded
                                                         {:expanded new-expanded}
                                                         {})))]
      (when next-route
        (store/dispatch! *:store [:nav/replace! next-route])))))

(defn ^:private ->on-drop [*:store]
  (fn [node-id [_ parent-id sibling-id]]
    (store/dispatch! *:store [::res/resubmit!
                              [::workspace#sync]
                              {::ws/id  node-id
                               ::action ::modify
                               :payload (cond-> {::ws/parent-id parent-id}
                                          sibling-id (assoc ::ws/prev-sibling-id sibling-id))}])))

(def create-modal
  [::edit! {:header       "Create new item in your workspace"
            :resource-key ws-form-key
            :params       {::action ::create}}])

(def modify-modal
  [::edit! {:header "Edit the workspace node"
            :params {::action ::modify}}])

(defn ->delete-modal
  "Return modal data for confirming deletion of a workspace node and its ancestors."
  [node]
  [:modals/sure?
   {:description  "This node and all ancestors will be deleted"
    :ok-btn-class ["delete-node"]
    :yes-commands [[::res/resubmit!
                    [::workspace#sync]
                    {::ws/id  (::ws/id node)
                     ::action ::destroy}]]}])

(defn ->note-edit-modal
  "Return modal descriptor for creating a new note prefilled with context and tags."
  [context tags]
  [::note-edit/modal
   {:init         {:notes/context     context
                   :notes/pinned?     true
                   :notes/tags        tags
                   :notes/attachments #{}}
    :header       "Create note"
    :params       {:ok-commands [[::res/submit! [::notes#pinned]]]}
    :resource-key create-note-key}])

(defn ->node-form-attrs
  "Return common form attributes for workspace node modals (create/modify)."
  [*:store form+ resource-key params]
  {:*:store      *:store
   :form+        form+
   :params       (assoc params :ok-commands [[:modals/remove-all!]])
   :resource-key resource-key})

(defn ->dnd-form-attrs
  "Return attributes for the drag-and-drop workspace form."
  [*:store]
  {:*:store *:store
   :comp    [drag-item *:store]
   :id      ::workspace
   :on-drop (->on-drop *:store)})
