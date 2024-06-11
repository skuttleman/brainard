(ns brainard.infra.views.pages.note
  "The page for viewing a note and editing its tags."
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.interfaces :as icomp]
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
                    :no-errors?   true
                    :resource-key pin-note-key
                    :params       {:ok-events  [[::res/swapped [::specs/notes#find (:notes/id note)]]]
                                   :err-events [[::forms/created pin-note-key init-form]]}}
        [ctrls/icon-toggle (-> {:*:store  *:store
                                :class    ["is-small"]
                                :disabled (res/requesting? form+)
                                :icon     :paperclip
                                :type     :submit}
                               (ctrls/with-attrs form+ [:notes/pinned?]))]]])
    (finally
      (store/emit! *:store [::forms+/destroyed pin-note-key]))))

(defmethod icomp/modal-header ::edit!
  [_ _]
  "Edit the note")

(defmethod icomp/modal-body ::edit!
  [*:store {modal-id :modals/id :keys [close! note]}]
  (r/with-let [sub:form+ (-> *:store
                             (store/dispatch! [::forms/ensure! update-note-key note])
                             (store/subscribe [::forms+/?:form+ update-note-key]))
               sub:tags (store/subscribe *:store [::res/?:resource [::specs/tags#select]])
               sub:contexts (store/subscribe *:store [::res/?:resource [::specs/contexts#select]])]
    [:div {:style {:min-width "40vw"}}
     [notes.views/note-form
      {:*:store      *:store
       :form+        @sub:form+
       :params       {:prev-tags   (:notes/tags note)
                      :ok-commands [[:modals/remove! modal-id]]
                      :ok-events   [[::res/swapped [::specs/notes#find (:notes/id note)]]
                                    [::forms/created pin-note-key]]}
       :resource-key update-note-key
       :sub:contexts sub:contexts
       :sub:tags     sub:tags
       :submit/body  "Save"
       :buttons      [[:button.button.is-cancel
                       {:on-click (fn [e]
                                    (dom/prevent-default! e)
                                    (close! e))}
                       "Cancel"]]}]]
    (finally
      (store/emit! *:store [::forms+/destroyed update-note-key]))))

(defn ^:private root [*:store note]
  (r/with-let [delete-modal [:modals/sure?
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
                                      (store/dispatch! *:store [:modals/create! [::edit! {:note note}]]))}
       "Edit"]
      [comp/plain-button {:class    ["is-danger"]
                          :on-click (fn [_]
                                      (store/dispatch! *:store [:modals/create! delete-modal]))}
       "Delete note"]]
     [sched.views/schedule-editor *:store note]]))

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
