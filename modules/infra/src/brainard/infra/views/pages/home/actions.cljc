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
(def ^:const fetch-ws-key [::specs/workspace#select])
(def ^:const create-ws-node-key [::forms+/valid [::workspace#create]])
(defn ->modify-node-key [node-id] [::forms+/valid [::workspace#modify node-id]])

(defmulti drag-item (fn [_ attrs _] (:type attrs)))

(forms+/validated ::notes#create (valid/->validator snotes/create)
  [_ {::forms/keys [data] :as spec}]
  (let [spec (assoc spec :payload (select-keys data #{:notes/context
                                                      :notes/pinned?
                                                      :notes/body
                                                      :notes/tags
                                                      :notes/attachments
                                                      :notes/todos}))]
    (specs/with-cbs (res/->request-spec [::specs/notes#create] spec)
                    :ok-events [[:api.notes/saved]]
                    :ok-commands [[:toasts.notes/succeed!]]
                    :err-commands [[:toasts/fail!]])))

(forms+/validated ::workspace#create (valid/->validator sws/create)
  [_ {::forms/keys [data] :as spec}]
  (let [spec (assoc spec :payload data)]
    (specs/with-cbs (res/->request-spec [::specs/workspace#create] spec)
                    :err-commands [[:toasts/fail!]])))

(defmethod res/->request-spec ::notes#pinned
  [_ spec]
  (res/->request-spec [::specs/notes#select] (assoc spec :params {:pinned true})))


(forms+/validated ::workspace#modify (valid/->validator sws/modify)
  [[_ resource-id] {::forms/keys [data] :as spec}]
  (let [spec (assoc spec :payload data)]
    (specs/with-cbs (res/->request-spec [::specs/workspace#modify resource-id] spec)
                    :err-commands [[:toasts/fail!]])))

(defn expanded-change [*:store route-info]
  (fn [_ _ old new]
    (let [old-expanded (::expanded (forms/data old))
          new-expanded (::expanded (forms/data new))
          next-route (when (not= old-expanded new-expanded)
                       (assoc route-info :query-params (if new-expanded
                                                         {:expanded new-expanded}
                                                         {})))]
      (when next-route
        (store/dispatch! *:store [:nav/replace! next-route])))))

(defn ->on-drop [*:store]
  (fn [node-id [_ parent-id sibling-id]]
    (store/dispatch! *:store [::res/submit!
                              [::specs/workspace#modify node-id]
                              {:payload      (cond-> {::ws/parent-id parent-id}
                                               sibling-id (assoc ::ws/prev-sibling-id sibling-id))
                               :ok-commands  [[::res/submit! fetch-ws-key]
                                              [:modals/remove-all!]]
                               :err-commands [[:toasts/fail!]]}])))

(def create-modal
  [::edit! {:header       "Create new item in your workspace"
            :resource-key create-ws-node-key}])

(def modify-modal
  [::edit! {:header "Edit the workspace node"}])

(defn ->delete-modal [node]
  [:modals/sure?
   {:description  "This node and all ancestors will be deleted"
    :yes-commands [[::res/submit!
                    [::specs/workspace#destroy (::ws/id node)]
                    {:ok-commands  [[::res/submit! [::specs/workspace#select]]]
                     :err-commands [[:toasts/fail!]]}]]}])

(defn ->note-edit-modal [context tags]
  [::note-edit/modal
   {:init         {:notes/context     context
                   :notes/pinned?     true
                   :notes/tags        tags
                   :notes/attachments #{}}
    :header       "Create note"
    :params       {:ok-commands [[::res/submit! [::notes#pinned]]]}
    :resource-key create-note-key}])

(defn ->node-form-attrs [*:store form+ resource-key]
  {:*:store      *:store
   :form+        form+
   :params       {:ok-commands [[::res/submit! fetch-ws-key]
                                [:modals/remove-all!]]}
   :resource-key resource-key})

(defn ->dnd-form-attrs [*:store]
  {:*:store *:store
   :comp    [drag-item *:store]
   :id      ::workspace
   :on-drop (->on-drop *:store)})
