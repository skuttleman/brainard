(ns brainard.infra.views.pages.home
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.drag-drop :as dnd]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]
    [workspace-nodes :as-alias ws]))

(def ^:private ^:const create-note-key [::forms+/valid [::specs/notes#create ::new-note]])
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
                      :class    ["is-small" "is-white"]
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
                                       {:ok-commands [[::res/submit! [::specs/workspace#select]]]}]]}]]
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

(defn ^:private collapsible [{:keys [*:store expanded? expand]} label & content]
  (cond-> [:div [:div.layout--row
                 [:div.layout--space-after label]
                 [comp/plain-button {:class    ["is-small" "is-white"]
                                     :on-click (fn [_]
                                                 (store/emit! *:store expand))}
                  [comp/icon (if expanded? :chevron-up :chevron-down)]]]]
    expanded? (into content)))

(defn ^:private tag-filter [{:keys [*:store form]} tags]
  (r/with-let [options (map #(vector % (str %)) tags)
               options-by-id (into {} options)]
    [ctrls/multi-dropdown (-> {:*:store       *:store
                               :inline?       true
                               :label         "Filter by tags"
                               :label-style   {:margin-bottom "16px"}
                               :options       options
                               :options-by-id options-by-id}
                              (ctrls/with-attrs form [::tag-filters]))]))

(defn ^:private ->on-drop [*:store]
  (fn [node-id [_ parent-id sibling-id]]
    (store/dispatch! *:store [::res/submit!
                              [::specs/workspace#move node-id]
                              {:body        (cond-> {::ws/parent-id parent-id}
                                              sibling-id (assoc ::ws/prev-sibling-id sibling-id))
                               :ok-commands [[::res/submit! fetch-ws-key]
                                             [:modals/remove-all!]]}])))

(defn ^:private expanded-change [*:store route-info]
  (fn [_ _ old new]
    (let [old-expanded (::expanded (forms/data old))
          new-expanded (::expanded (forms/data new))
          next-route (when (not= old-expanded new-expanded)
                       (assoc route-info :query-params (if new-expanded
                                                         {:expanded new-expanded}
                                                         {})))]
      (when next-route
        (store/dispatch! *:store [:nav/replace! next-route])))))

(defmethod icomp/modal-header ::new-note
  [_ _]
  "New note")

(defmethod icomp/modal-body ::new-note
  [*:store {:modals/keys [close! id] :keys [sub:contexts sub:tags context tags] :as params}]
  (r/with-let [new-note {:notes/context context
                         :notes/pinned? true
                         :notes/tags    tags}
               sub:form+ (-> *:store
                             (store/emit! [::forms/created create-note-key new-note])
                             (store/subscribe [::forms+/?:form+ create-note-key]))]
    [:div {:style {:min-width "50vw"}}
     [comp/pprint params]
     [notes.views/note-form
      {:*:store      *:store
       :form+        @sub:form+
       :params       {:ok-commands [[::res/submit! [::specs/notes#pinned]]
                                    [:modals/remove! id]]}
       :resource-key create-note-key
       :sub:contexts sub:contexts
       :sub:tags     sub:tags
       :buttons      [[comp/plain-button {:on-click close!}
                       "Cancel"]]}]]
    (finally
      (store/emit! *:store [::forms+/destroyed create-node-key]))))

(defn ^:private pinned [*:store sub:contexts sub:tags route-info [tags pinned-notes]]
  (r/with-let [sub:form (-> *:store
                            (store/dispatch! [::forms/ensure!
                                              [::expanded-group]
                                              {::expanded    (-> route-info :query-params :expanded)
                                               ::tag-filters #{}}])
                            (store/subscribe [::forms/?:form [::expanded-group]])
                            (doto (add-watch ::change (expanded-change *:store route-info))))]
    (let [form @sub:form
          {::keys [expanded tag-filters]} (forms/data form)
          form-id (forms/id form)
          filtered-notes (filter (fn [{:notes/keys [tags]}]
                                   (set/subset? tag-filters tags))
                                 pinned-notes)]
      [:section
       [:h1 {:style {:font-size "1.5rem"}} [:strong "Pinned notes"]]
       [:div.layout--space-between
        [tag-filter {:*:store *:store
                     :form    form}
         tags]
        [comp/plain-button
         {:class    ["is-info"]
          :on-click (fn [_]
                      (store/dispatch! *:store
                                       [:modals/create!
                                        [::new-note {:sub:contexts sub:contexts
                                                     :sub:tags     sub:tags
                                                     :context      expanded
                                                     :tags         tag-filters}]]))}
         "Create note"]]
       (if (seq filtered-notes)
         (for [[context note-group] (group-by :notes/context filtered-notes)
               :let [expanded? (= context expanded)
                     next-context (when-not expanded? context)]]
           ^{:key context}
           [collapsible {:*:store   *:store
                         :expanded? expanded?
                         :expand    [::forms/changed form-id [::expanded] next-context]}
            [:strong context]
            [:div {:style {:margin-left "12px"}}
             [notes.views/note-list
              {:anchor        (:anchor route-info)
               :anchor?       true
               :hide-context? true}
              note-group]]])
         [:em "No pinned notes"])])
    (finally
      (remove-watch sub:form ::change)
      (store/emit! *:store [::forms/destroyed [::expanded-group]]))))

(defn workspace [*:store ws-nodes]
  (r/with-let [dnd-attrs {:*:store *:store
                          :comp    [drag-item *:store]
                          :id      ::workspace
                          :on-drop (->on-drop *:store)}]
    [:section
     [:h1 {:style {:font-size "1.5rem"}} [:strong "Workspace"]]
     [dnd/control dnd-attrs (->tree ws-nodes)]
     [icon-button *:store create-modal :plus]]))

(defmethod ipages/page :routes.ui/home
  [*:store route-info]
  (r/with-let [sub:contexts (store/subscribe *:store [::res/?:resource [::specs/contexts#select]])
               sub:tags (store/subscribe *:store [::res/?:resource [::specs/tags#select]])
               sub:pinned (-> *:store
                              (store/dispatch! [::res/ensure! [::specs/notes#pinned]])
                              (store/subscribe [::res/?:resource [::specs/notes#pinned]]))
               sub:tree (-> *:store
                            (store/dispatch! [::res/ensure! fetch-ws-key])
                            (store/subscribe [::res/?:resource fetch-ws-key]))]
    [:div.layout--stack-between
     [comp/with-resource sub:tree [workspace *:store]]
     [comp/with-resources [sub:tags sub:pinned] [pinned *:store sub:contexts sub:tags route-info]]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/notes#pinned]])
      (store/emit! *:store [::res/destroyed fetch-ws-key]))))
