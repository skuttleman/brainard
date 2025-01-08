(ns brainard.infra.views.pages.home.core
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.drag-drop :as dnd]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.infra.views.pages.home.actions :as home.act]
    [brainard.notes.infra.views :as notes.views]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]
    [workspace-nodes :as-alias ws]))

(defn ^:private collapsible [{:keys [*:store expanded? expand]} label & content]
  (cond-> [:div [:div.layout--row
                 [:div.layout--space-after label]
                 [comp/plain-button {:*:store *:store
                                     :class   ["is-small" "is-white"]
                                     :events  [expand]}
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

(defn ^:private pinned [*:store route-info [tags pinned-notes]]
  (r/with-let [sub:form (-> *:store
                            (store/form-sub [::expanded-group]
                                            {::expanded    (-> route-info :query-params :expanded)
                                             ::tag-filters #{}})
                            (doto (add-watch ::change (home.act/expanded-change *:store route-info))))]
    (let [form @sub:form
          {::keys [expanded tag-filters]} (forms/data form)
          form-id (forms/id form)
          filtered-notes (filter (fn [{:notes/keys [tags]}]
                                   (set/subset? tag-filters tags))
                                 pinned-notes)
          edit-modal (home.act/->note-edit-modal expanded tag-filters)]
      [:section.box
       [:h1 {:style {:font-size "1.5rem"}} [:strong "Pinned notes"]]
       [:div.layout--row
        [:div.layout--space-after
         [comp/plain-button
          {:*:store  *:store
           :class    ["is-info"]
           :commands [[:modals/create! edit-modal]]}
          "Create note"]]
        [tag-filter {:*:store *:store
                     :form    form}
         tags]]
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

(defn ^:private ->tree [ws-nodes]
  (walk/postwalk (fn [x]
                   (cond-> x
                     (map? x) (merge (set/rename-keys x {::ws/id        :id
                                                         ::ws/parent-id :parent-id
                                                         ::ws/children  :children}))))
                 ws-nodes))

(defn ^:private icon-button [*:store modal icon]
  [comp/plain-button {:*:store  *:store
                      :commands [[:modals/create! modal]]
                      :class    ["is-small" "is-white"]
                      :style    {:padding 0 :height "2em"}}
   [comp/icon (when (= :trash-can icon) {:class ["is-danger"]}) icon]])

(defmethod home.act/drag-item :static
  [*:store {:keys [on-drag-begin]} node]
  (let [create-modal (update home.act/create-modal 1 assoc
                             :init-data {::ws/parent-id (:id node)})
        modify-modal (update home.act/modify-modal 1 merge
                             {:init-data    (select-keys node #{::ws/id
                                                                ::ws/content})
                              :resource-key (home.act/->modify-node-key (::ws/id node))})
        delete-modal (home.act/->delete-modal node)]
    [:div.layout--row
     [:span.layout--space-after {:on-mouse-down on-drag-begin}
      [:span {:style {:cursor :grab}} (::ws/content node)]]
     [:span.layout--space-after
      [icon-button *:store modify-modal :pencil]]
     [:span.layout--space-after
      [icon-button *:store delete-modal :trash-can]]
     [icon-button *:store create-modal :plus]]))

(defmethod home.act/drag-item :default
  [_ _ node]
  [:span (::ws/content node)])

(defmethod icomp/modal-header ::home.act/edit!
  [_ {:keys [header]}]
  header)

(defmethod icomp/modal-body ::home.act/edit!
  [*:store {:keys [init-data resource-key]}]
  (r/with-let [sub:form+ (store/form+-sub *:store resource-key init-data)]
    (let [form+ @sub:form+]
      [ctrls/form (home.act/->node-form-attrs *:store form+ resource-key)
       [ctrls/input (-> {:*:store     *:store
                         :auto-focus? true
                         :label       "Content"}
                        (ctrls/with-attrs form+ [::ws/content]))]])
    (finally
      (store/emit! *:store [::forms+/destroyed resource-key]))))

(defn ^:private workspace [*:store ws-nodes]
  (r/with-let [dnd-attrs (home.act/->dnd-form-attrs *:store)]
    [:section.box
     [:h1 {:style {:font-size "1.5rem"}} [:strong "Workspace"]]
     [dnd/control dnd-attrs (->tree ws-nodes)]
     [icon-button *:store home.act/create-modal :plus]]))

(defmethod ipages/page :routes.ui/home
  [*:store route-info]
  (r/with-let [sub:tags (store/res-sub *:store [::specs/tags#select])
               sub:pinned (store/res-sub *:store [::home.act/notes#pinned])
               sub:tree (store/res-sub *:store home.act/fetch-ws-key)]
    [:div.layout--stack-between
     [comp/with-resource sub:tree [workspace *:store]]
     [comp/with-resources [sub:tags sub:pinned] [pinned *:store route-info]]]
    (finally
      (store/emit! *:store [::res/destroyed home.act/fetch-ws-key])
      (store/emit! *:store [::res/destroyed [::home.act/notes#pinned]]))))
