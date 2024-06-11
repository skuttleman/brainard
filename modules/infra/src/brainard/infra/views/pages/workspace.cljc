(ns brainard.infra.views.pages.workspace
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.drag-drop :as dnd]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]
    [workspace-nodes :as-alias ws]))

(def ^:private fetch-ws-key [::specs/workspace#select])
(def ^:private create-node-key [::forms+/valid [::specs/workspace#create]])
(defn ^:private ->modify-node-key [node-id] [::forms+/valid [::specs/workspace#modify node-id]])

(defmulti ^:private drag-item (fn [_ attrs _] (:type attrs)))

(defn ^:private ->tree [ws-nodes]
  (walk/postwalk (fn [x]
                   (cond-> x
                     (map? x) (merge (set/rename-keys x {::ws/id        :id
                                                         ::ws/parent-id :parent-id
                                                         ::ws/children  :children}))))
                 ws-nodes))

(defmethod icomp/modal-header ::edit!
  [_ {:keys [header]}]
  header)

(defmethod icomp/modal-body ::edit!
  [*:store {:keys [init-data resource-key]}]
  (r/with-let [sub:form+ (-> *:store
                             (store/emit! [::forms/created resource-key init-data])
                             (store/subscribe [::forms+/?:form+ resource-key]))]
    (let [form+ @sub:form+]
      [ctrls/form {:*:store      *:store
                   :form+        form+
                   :params       {:ok-commands [[::res/submit! fetch-ws-key]
                                                [:modals/remove-all!]]}
                   :resource-key resource-key}
       [ctrls/input (-> {:*:store     *:store
                         :auto-focus? true
                         :label       "Content"}
                        (ctrls/with-attrs form+ [::ws/content]))]])
    (finally
      (store/emit! *:store [::forms+/destroyed resource-key]))))

(def ^:private create-modal
  [::edit! {:header       "Create new item in your workspace"
            :resource-key create-node-key}])

(def ^:private modify-modal
  [::edit! {:header "Edit the workspace node"}])

(defn ^:private icon-button [*:store modal icon]
  [comp/plain-button {:on-click (fn [_]
                                  (store/dispatch! *:store [:modals/create! modal]))
                      :class    ["is-small" "is-ghost"]
                      :style    {:padding 0 :height "2em"}}
   [comp/icon (when (= :trash-can icon) {:class ["is-danger"]}) icon]])

(defmethod drag-item :static
  [*:store {:keys [on-drag-begin]} node]
  (let [create-modal (update create-modal 1 assoc :init-data {::ws/parent-id (:id node)})
        modify-modal (update modify-modal 1 merge {:init-data    (select-keys node #{::ws/id ::ws/content})
                                                   :resource-key (->modify-node-key (::ws/id node))})
        delete-modal [:modals/sure?
                      {:description  "This node and all ancestors will be deleted"
                       :yes-commands [[::res/submit!
                                       [::specs/workspace#destroy (::ws/id node)]
                                       {:ok-commands [[::res/submit! [::workspace#select]]]}]]}]]
    [:div.layout--row
     [:span.layout--space-after {:on-mouse-down on-drag-begin}
      [:span {:style {:cursor :grab}} (::ws/content node)]]
     [:span.layout--space-after
      [icon-button *:store modify-modal :pencil]]
     [:span.layout--space-after
      [icon-button *:store delete-modal :trash-can]]
     [icon-button *:store create-modal :plus]]))

(defmethod drag-item :default
  [_ _ node]
  [:span (::ws/content node)])

(defn ^:private ->on-drop [*:store]
  (fn [node-id [_ parent-id sibling-id]]
    (store/dispatch! *:store [::res/submit!
                              [::specs/workspace#move node-id]
                              {:body        (cond-> {::ws/parent-id parent-id}
                                              sibling-id (assoc ::ws/prev-sibling-id sibling-id))
                               :ok-commands [[::res/submit! fetch-ws-key]
                                             [:modals/remove-all!]]}])))

(defn root [*:store ws-nodes]
  (r/with-let [dnd-attrs {:*:store *:store
                          :comp    [drag-item *:store]
                          :id      ::workspace
                          :on-drop (->on-drop *:store)}]
    [:div
     [:h1.subtitle "Welcome to your workspace"]
     [dnd/control dnd-attrs (->tree ws-nodes)]
     [icon-button *:store create-modal :plus]]))

(defmethod ipages/page :routes.ui/workspace
  [*:store _]
  (r/with-let [sub:tree (-> *:store
                            (store/dispatch! [::res/ensure! fetch-ws-key])
                            (store/subscribe [::res/?:resource fetch-ws-key]))]
    [comp/with-resource sub:tree [root *:store]]
    (finally
      (store/emit! *:store [::res/destroyed fetch-ws-key]))))
