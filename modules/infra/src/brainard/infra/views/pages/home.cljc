(ns brainard.infra.views.pages.home
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.fragments.note-edit :as note-edit]
    [brainard.infra.views.fragments.workspace :as workspace]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
    [clojure.set :as set]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]))

(def ^:private ^:const create-note-key [::forms+/valid [::specs/notes#create ::new-note]])

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



(defn ^:private pinned [*:store route-info [tags pinned-notes]]
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
                                 pinned-notes)
          edit-modal [::note-edit/modal
                      {:init         {:notes/context expanded
                                      :notes/pinned? true
                                      :notes/tags    tags}
                       :header       "Create note"
                       :params       {:ok-commands [[::res/submit! [::specs/notes#pinned]]]}
                       :resource-key create-note-key}]]
      [:section
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

(defmethod ipages/page :routes.ui/home
  [*:store route-info]
  (r/with-let [sub:tags (store/subscribe *:store [::res/?:resource [::specs/tags#select]])
               sub:pinned (-> *:store
                              (store/dispatch! [::res/ensure! [::specs/notes#pinned]])
                              (store/subscribe [::res/?:resource [::specs/notes#pinned]]))]
    [:div.layout--stack-between
     [workspace/fragment *:store]
     [comp/with-resources [sub:tags sub:pinned] [pinned *:store route-info]]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/notes#pinned]]))))
