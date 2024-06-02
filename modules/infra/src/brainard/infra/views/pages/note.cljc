(ns brainard.infra.views.pages.note
  "The page for viewing a note and editing its tags."
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.schedules.infra.views :as sched.views]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as res]
    [whet.utils.navigation :as nav]
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
  (let [modal [:modals/sure?
               {:description  "This note and all related schedules will be deleted"
                :yes-commands [[::res/submit! [::specs/notes#destroy (:notes/id note)]]]}]]
    [:div.layout--stack-between
     (if-let [tags (not-empty (:notes/tags note))]
       [comp/tag-list {:value tags}]
       [:em "no tags"])
     [:div.button-row
      [comp/plain-button {:class ["is-info"]
                          :on-click (fn [_]
                                      (store/emit! *:store [::forms/changed update-note-key [::editing?] true]))}
       "Edit tags"]
      [comp/plain-button {:class    ["is-danger"]
                          :on-click (fn [_]
                                      (store/dispatch! *:store [:modals/create! modal]))}
       "Delete note"]]]))

(defn ^:private pin-toggle [*:store note]
  (r/with-let [init-form (select-keys note #{:notes/id :notes/pinned?})
               sub:form+ (-> *:store
                             (store/dispatch! [::forms/ensure! pin-note-key init-form])
                             (store/subscribe [::forms+/?:form+ pin-note-key]))]
    (let [form+ @sub:form+]
      [:div
       [ctrls/form {:*:store      *:store
                    :form+        form+
                    :horizontal?  true
                    :no-buttons?  true
                    :resource-key pin-note-key}
        [ctrls/icon-toggle (-> {:*:store  *:store
                                :class    ["is-small"]
                                :disabled (res/requesting? form+)
                                :icon     :paperclip
                                :type     :submit}
                               (ctrls/with-attrs form+ [:notes/pinned?]))]]])
    (finally
      (store/emit! *:store [::forms+/destroyed pin-note-key]))))

(defn ^:private root [{:keys [*:store]} note]
  (r/with-let [init-form (select-keys note #{:notes/tags})
               sub:form+ (-> *:store
                             (store/dispatch! [::forms/ensure! update-note-key init-form])
                             (store/subscribe [::forms+/?:form+ update-note-key]))
               sub:tags (store/subscribe *:store [::res/?:resource [::specs/tags#select]])]
    [:div.layout--stack-between
     [:div.layout--row
      [:h1.layout--space-after.flex-grow [:strong (:notes/context note)]]
      [pin-toggle *:store note]]
     [comp/markdown (:notes/body note)]
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
    (r/with-let [sub:note (-> *:store
                              (store/dispatch! [::res/ensure! resource-key])
                              (store/subscribe [::res/?:resource resource-key]))]
      (let [resource @sub:note]
        (cond
          (res/success? resource)
          [root {:*:store *:store} (res/payload resource)]

          (res/error? resource)
          [comp/alert :warn
           [:div
            "Note not found. Try "
            [:a.link {:href (nav/path-for rte/all-routes :routes.ui/main)} "creating one"]
            "."]]

          :else
          [comp/spinner]))
      (finally
        (store/emit! *:store [::res/destroyed resource-key])))))
