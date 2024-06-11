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
    [brainard.notes.infra.views :as notes.views]
    [brainard.schedules.infra.views :as sched.views]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as res]
    [whet.utils.navigation :as nav]
    [whet.utils.reagent :as r]))

(def ^:private ^:const update-note-key [::forms+/std [::specs/notes#update ::forms/edit-note]])
(def ^:private ^:const pin-note-key [::forms+/std [::specs/notes#pin ::forms/pin-note]])

(defn ^:private tag-list [note]
  (if-let [tags (not-empty (:notes/tags note))]
    [comp/tag-list {:value tags}]
    [:em "no tags"]))

(defn ^:private pin-toggle [*:store note]
  (r/with-let [init-form (select-keys note #{:notes/id :notes/pinned?})
               sub:form+ (-> *:store
                             (store/dispatch! [::forms/ensure! pin-note-key init-form])
                             (store/subscribe [::forms+/?:form+ pin-note-key]))]
    (let [form+ @sub:form+]
      [:div
       [ctrls/form {:*:store      *:store
                    :form+        form+
                    :no-buttons?  true
                    :resource-key pin-note-key
                    :params {:ok-commands [[::res/swap! [::specs/notes#find (:notes/id note)]]]}}
        [ctrls/icon-toggle (-> {:*:store  *:store
                                :class    ["is-small"]
                                :disabled (res/requesting? form+)
                                :icon     :paperclip
                                :type     :submit}
                               (ctrls/with-attrs form+ [:notes/pinned?]))]]])
    (finally
      (store/emit! *:store [::forms+/destroyed pin-note-key]))))

(defn ^:private note-view [*:store note]
  (r/with-let [modal [:modals/sure?
                      {:description  "This note and all related schedules will be deleted"
                       :yes-commands [[::res/submit!
                                       [::specs/notes#destroy (:notes/id note)]
                                       {:ok-commands [[:nav/navigate! {:token :routes.ui/home}]]}]]}]]
    [:div.layout--stack-between
     [:div.layout--row
      [:h1.layout--space-after.flex-grow [:strong (:notes/context note)]]
      [pin-toggle *:store note]]
     [comp/markdown (:notes/body note)]
     [tag-list note]
     [:div.button-row
      [comp/plain-button {:class    ["is-info"]
                          :on-click (fn [_]
                                      (store/emit! *:store [::forms/changed
                                                            update-note-key
                                                            [::editing?]
                                                            true]))}
       "Edit"]
      [comp/plain-button {:class    ["is-danger"]
                          :on-click (fn [_]
                                      (store/dispatch! *:store [:modals/create! modal]))}
       "Delete note"]]
     [sched.views/schedule-editor *:store note]]))

(defn ^:private root [*:store note]
  (r/with-let [sub:form+ (-> *:store
                             (store/dispatch! [::forms/ensure! update-note-key note])
                             (store/subscribe [::forms+/?:form+ update-note-key]))
               sub:tags (store/subscribe *:store [::res/?:resource [::specs/tags#select]])
               sub:contexts (store/subscribe *:store [::res/?:resource [::specs/contexts#select]])]
    (if (::editing? (forms/data @sub:form+))
      [notes.views/note-form
       {:*:store      *:store
        :form+        @sub:form+
        :params       {:prev-tags (:notes/tags note)
                       :ok-commands [[::res/swap! [::specs/notes#find (:notes/id note)]]]}
        :resource-key update-note-key
        :sub:contexts sub:contexts
        :sub:tags     sub:tags
        :submit/body  "Save"
        :buttons      [[:button.button.is-cancel
                        {:on-click (fn [e]
                                     (dom/prevent-default! e)
                                     (store/emit! *:store [::forms/created update-note-key note]))}
                        "Cancel"]]}]
      [note-view *:store note])
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
          [root *:store (res/payload resource)]

          (res/error? resource)
          [comp/alert :warn
           [:div
            "Note not found. Try "
            [:a.link {:href (nav/path-for rte/all-routes :routes.ui/home)} "creating one"]
            "."]]

          :else
          [comp/spinner]))
      (finally
        (store/emit! *:store [::res/destroyed resource-key])))))
