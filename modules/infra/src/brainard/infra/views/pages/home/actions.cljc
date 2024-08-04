(ns brainard.infra.views.pages.home.actions
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.fragments.note-edit :as-alias note-edit]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as res]
    [workspace-nodes :as-alias ws]))

(def ^:const create-note-key [::forms+/valid [::specs/notes#create]])
(def ^:const fetch-ws-key [::specs/workspace#select])
(def ^:const create-ws-node-key [::forms+/valid [::specs/workspace#create]])
(defn ->modify-node-key [node-id] [::forms+/valid [::specs/workspace#modify node-id]])

(defmulti drag-item (fn [_ attrs _] (:type attrs)))

(defmethod res/->request-spec ::notes#pinned
  [_ spec]
  (res/->request-spec [::specs/notes#select] (assoc spec ::forms/data {:pinned true})))

(defmethod res/->request-spec ::workspace#move
  [[_ resource-id] {:keys [body] :as spec}]
  (res/->request-spec [::specs/workspace#modify resource-id] (assoc spec ::forms/data body)))

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
                              [::workspace#move node-id]
                              {:body        (cond-> {::ws/parent-id parent-id}
                                              sibling-id (assoc ::ws/prev-sibling-id sibling-id))
                               :ok-commands [[::res/submit! fetch-ws-key]
                                             [:modals/remove-all!]]}])))

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
                    {:ok-commands [[::res/submit! [::specs/workspace#select]]]}]]}])

(defn ->note-edit-modal [context tags]
  [::note-edit/modal
   {:init         {:notes/context context
                   :notes/pinned? true
                   :notes/tags    tags}
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