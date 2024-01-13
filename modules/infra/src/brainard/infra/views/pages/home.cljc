(ns brainard.infra.views.pages.home
  "The home page with note creation form."
  (:require
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.stubs.reagent :as r]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]))

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

(defn ^:private search-results [route-info [notes]]
  [:div
   (if (seq notes)
     [:<>
      [:h3.subtitle [:em "Some related notes..."]]
      [notes.views/search-results route-info notes]]
     [:em "Brand new topic!"])])

(defn ^:private root* [{:keys [*:store form+ sub:contexts sub:tags]}]
  [ctrls/form {:*:store      *:store
               :form+        form+
               :params       {:pre-events [[::res/destroyed select-notes-key]]}
               :resource-key create-note-key}
   [:strong "Create a note"]
   [ctrls/type-ahead (-> {:*:store   *:store
                          :label     "Topic"
                          :sub:items sub:contexts
                          :on-blur   (->context-blur *:store)}
                         (ctrls/with-attrs form+ [:notes/context]))]
   [ctrls/textarea (-> {:label   "Body"
                        :*:store *:store}
                       (ctrls/with-attrs form+ [:notes/body]))]
   [ctrls/tags-editor (-> {:*:store   *:store
                           :form-id   [::tags form-id]
                           :label     "Tags"
                           :sub:items sub:tags}
                          (ctrls/with-attrs form+ [:notes/tags]))]])

(defmethod ipages/page :routes.ui/home
  [{:keys [*:store] :as route-info}]
  (r/with-let [sub:form+ (do (store/dispatch! *:store [::forms/ensure! create-note-key new-note])
                             (store/subscribe *:store [::forms+/?:form+ create-note-key]))
               sub:contexts (store/subscribe *:store [::res/?:resource [::specs/contexts#select]])
               sub:tags (store/subscribe *:store [::res/?:resource [::specs/tags#select]])
               sub:notes (store/subscribe *:store [::res/?:resource select-notes-key])]
    [:div
     [root* {:*:store      *:store
             :form+        @sub:form+
             :sub:contexts sub:contexts
             :sub:tags     sub:tags}]
     [comp/with-resources [sub:notes] [search-results (assoc route-info :hide-init? true)]]]
    (finally
      (store/emit! *:store [::forms+/destroyed create-note-key])
      (store/emit! *:store [::res/destroyed select-notes-key]))))
