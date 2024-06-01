(ns brainard.infra.views.pages.note
  "The page for viewing a note and editing its tags."
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.schedules.infra.views :as sched.views]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(def ^:private ^:const form-id ::forms/edit-note)
(def ^:private ^:const update-note-key [::forms+/std [::specs/notes#update form-id]])

(def ^:private ^:const pinned-form-id ::forms/pin-note)
(def ^:private ^:const pin-note-key [::forms+/std [::specs/notes#pin pinned-form-id]])

(defn ^:private tag-editor [{:keys [*:store sub:form+ sub:tags note]}]
  (let [form+ @sub:form+]
    [ctrls/form {:*:store      *:store
                 :form+        form+
                 :params       {:note   note
                                :fetch? true}
                 :resource-key update-note-key
                 :submit/body  "Save"
                 :buttons      [[:button.button.is-cancel
                                 {:on-click (fn [e]
                                              (dom/prevent-default! e)
                                              (store/emit! *:store
                                                           [::forms/created update-note-key
                                                            (select-keys note #{:notes/tags})]))}
                                 "Cancel"]]}
     [ctrls/tags-editor (-> {:*:store   *:store
                             :form-id   [::tags form-id]
                             :label     "Tags"
                             :sub:items sub:tags}
                            (ctrls/with-attrs form+ [:notes/tags]))]]))

(defn ^:private tag-list [*:store note]
  [:div.layout--space-between
   (if-let [tags (not-empty (:notes/tags note))]
     [comp/tag-list {:value tags}]
     [:em "no tags"])
   [:button.button.is-info
    {:disabled #?(:clj true :default false)
     :on-click (fn [_]
                 (store/emit! *:store [::forms/changed update-note-key [::editing?] true]))}
    "edit tags"]])

(defn ^:private pin-toggle [*:store note]
  (r/with-let [init-form (select-keys note #{:notes/id :notes/pinned?})
               sub:form+ (do (store/dispatch! *:store [::forms/ensure! pin-note-key init-form])
                             (store/subscribe *:store [::forms+/?:form+ pin-note-key]))]
    (let [form+ @sub:form+]
      [:div
       [ctrls/form {:*:store      *:store
                    :form+        form+
                    :horizontal?  true
                    :no-buttons?  true
                    :resource-key pin-note-key}
        [ctrls/icon-toggle (-> {:*:store *:store
                                :icon    :paperclip
                                :class ["is-small"]}
                               (ctrls/with-attrs form+ [:notes/pinned?]))]]])
    (finally
      (store/emit! *:store [::forms+/destroyed pin-note-key]))))

(defn ^:private root [{:keys [*:store]} [note]]
  (r/with-let [init-form (select-keys note #{:notes/tags})
               sub:form+ (do (store/dispatch! *:store [::forms/ensure! update-note-key init-form])
                             (store/subscribe *:store [::forms+/?:form+ update-note-key]))
               sub:tags (store/subscribe *:store [::res/?:resource [::specs/tags#select]])]
    [:div.layout--stack-between
     [:div.layout--row
      [:h1.layout--space-after [:strong (:notes/context note)]]
      [pin-toggle *:store note]]
     [:p (:notes/body note)]
     (if (::editing? (forms/data @sub:form+))
       [tag-editor {:*:store   *:store
                    :sub:form+ sub:form+
                    :sub:tags  sub:tags
                    :note      note}]
       [tag-list *:store note])
     [sched.views/editor *:store note]]
    (finally
      (store/emit! *:store [::forms+/destroyed update-note-key]))))

(defmethod ipages/page :routes.ui/note
  [*:store {:keys [route-params]}]
  (let [resource-key [::specs/notes#find (:notes/id route-params)]]
    (r/with-let [sub:note (do (store/dispatch! *:store [::res/ensure! resource-key])
                              (store/subscribe *:store [::res/?:resource resource-key]))]
      [comp/with-resources [sub:note] [root {:*:store *:store}]]
      (finally
        (store/emit! *:store [::res/destroyed resource-key])))))
