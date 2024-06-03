(ns brainard.infra.views.pages.home
  "The home page with note creation form."
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(def ^:private ^:const form-id ::forms/new-note)
(def ^:private ^:const create-note-key [::forms+/valid [::specs/notes#create form-id]])
(def ^:private ^:const select-notes-key [::specs/notes#select form-id])

(def ^:private new-note
  {:notes/body    nil
   :notes/context nil
   :notes/tags    #{}})

(defn ^:private ->context-blur [store]
  (fn [e]
    (store/dispatch! store
                     [::res/sync!
                      select-notes-key
                      {::forms/data {:notes/context (dom/target-value e)}}])))

(defn ^:private search-results [_attrs notes]
  [:div
   (if (seq notes)
     [:<>
      [:h3.subtitle [:em "Some related notes..."]]
      [notes.views/note-list {} notes]]
     [:em "Brand new topic!"])])

(defn ^:private root [{:keys [*:store form+ sub:contexts sub:tags]}]
  (let [form-data (forms/data form+)]
    [ctrls/form {:*:store      *:store
                 :form+        form+
                 :params       {:pre-events [[::res/destroyed select-notes-key]]}
                 :resource-key create-note-key}
     [:strong "Create a note"]
     [:div.layout--space-between
      [:div.flex-grow
       [ctrls/type-ahead (-> {:*:store     *:store
                              :label       "Topic"
                              :sub:items   sub:contexts
                              :auto-focus? true
                              :on-blur     (->context-blur *:store)}
                             (ctrls/with-attrs form+ [:notes/context]))]]
      [ctrls/icon-toggle (-> {:*:store *:store
                              :label   "Pinned"
                              :icon    :paperclip}
                             (ctrls/with-attrs form+ [:notes/pinned?]))]]
     [:label.label "Body"]
     [:div {:style {:margin-top 0}}
      (if (::preview? form-data)
        [:div.expanded
         [comp/markdown (:notes/body form-data)]]
        [ctrls/textarea (-> {:style   {:font-family :monospace
                                       :min-height  "250px"}
                             :*:store *:store}
                            (ctrls/with-attrs form+ [:notes/body]))])]
     [ctrls/toggle (-> {:label   [:span.is-small "Preview"]
                        :style   {:margin-top 0}
                        :inline? true
                        :*:store *:store}
                       (ctrls/with-attrs form+ [::preview?]))]
     [ctrls/tags-editor (-> {:*:store   *:store
                             :form-id   [::tags form-id]
                             :label     "Tags"
                             :sub:items sub:tags}
                            (ctrls/with-attrs form+ [:notes/tags]))]]))

(defmethod ipages/page :routes.ui/main
  [*:store _route-info]
  (r/with-let [sub:form+ (-> *:store
                             (store/dispatch! [::forms/ensure! create-note-key new-note])
                             (store/subscribe [::forms+/?:form+ create-note-key]))
               sub:contexts (store/subscribe *:store [::res/?:resource [::specs/contexts#select]])
               sub:tags (store/subscribe *:store [::res/?:resource [::specs/tags#select]])
               sub:notes (store/subscribe *:store [::res/?:resource select-notes-key])]
    [:div
     [root {:*:store      *:store
            :form+        @sub:form+
            :sub:contexts sub:contexts
            :sub:tags     sub:tags}]
     [comp/with-resource sub:notes [search-results {:hide-init? true}]]]
    (finally
      (store/emit! *:store [::forms+/destroyed create-note-key])
      (store/emit! *:store [::res/destroyed select-notes-key]))))
