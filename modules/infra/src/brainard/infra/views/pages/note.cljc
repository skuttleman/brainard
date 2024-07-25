(ns brainard.infra.views.pages.note
  "The page for viewing a note and editing its tags."
  (:require
    [brainard.api.utils.dates :as dates]
    [brainard.infra.store.core :as store]
    [brainard.infra.views.fragments.note-edit :as note-edit]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.schedules.infra.views :as sched.views]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as res]
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

(defn ^:private history-change [label {:keys [added from removed to]}]
  [:div.layout--row
   [:span.purple label]
   (when (some? added)
     [:<>
      [:em.space--left "added"]
      [:span.space--left.truncate.blue
       (apply str (interpose ", " added))]])
   (when (some? removed)
     [:<>
      [:em.space--left "removed"]
      [:span.space--left.truncate.orange
       (apply str (interpose ", " removed))]])
   (cond
     (and (some? from) (some? to)) [:<>
                                    [:em.space--left "changed"]
                                    [:span.space--left.truncate.orange {:style {:max-width "50%"}}
                                     (str from)]
                                    [:em.space--left "to"]
                                    [:span.space--left.truncate.blue {:style {:max-width "50%"}}
                                     (str to)]]
     (some? from) [:<>
                   [:em.space--left "removed"]
                   [:span.space--left.truncate.orange
                    (str from)]]
     (some? to) [:<>
                 [:em.space--left "added"]
                 [:span.space--left.truncate.blue
                  (str to)]])])

(defmethod icomp/modal-header ::view
  [_ {:notes/keys [saved-at]}]
  (dates/->str saved-at))

(defmethod icomp/modal-body ::view
  [*:store {modal-id :modals/id :keys [last? note] :as params}]
  [:div.layout--stack-between
   [:div.layout--row
    (when (:notes/pinned? note)
      [comp/icon {:class ["layout--space-after"]
                  :style {:align-self :center}} :paperclip])
    [:h1 [:strong (:notes/context note)]]]
   [comp/markdown (:notes/body note)]
   [tag-list (:notes/tags note)]
   (when-not last?
     [:div
      [comp/plain-button {:*:store  *:store
                          :class    ["is-small" "is-info"]
                          :commands [[:modals/remove! modal-id]
                                     [::res/submit! [::specs/notes#reinstate modal-id] params]]}
       "reinstate"]])])

(defn ^:private note-history [*:store reconstruction entries]
  (let [entry-count (count entries)
        prev-tags (:notes/tags (get reconstruction (:notes/history-id (last entries))))]
    [:ul.note-history
     (for [[idx {:notes/keys [changes history-id saved-at]}] (map-indexed vector entries)
           :let [history-modal [::view {:last?     (= idx (dec entry-count))
                                        :note      (get reconstruction history-id)
                                        :prev-tags prev-tags}]]]
       ^{:key history-id}
       [:li.layout--stack-between
        [:div.layout--row.layout--align-center.layout--space-between
         [:div
          [:span.layout--space-after.green (dates/->str saved-at)]]
         [comp/plain-button {:*:store  *:store
                             :class    ["is-small" "is-info"]
                             :commands [[:modals/create! history-modal]]}
          "show"]]
        (into [:<>]
              (for [[k label] [[:notes/context "Context"]
                               [:notes/pinned? "Pin"]
                               [:notes/body "Body"]
                               [:notes/tags "Tags"]]
                    :let [change (k changes)]
                    :when change]
                [history-change label change]))])]))

(defmethod icomp/modal-header ::history
  [_ _]
  "Note's change history")

(defmethod icomp/modal-body ::history
  [*:store {{note-id :notes/id} :note}]
  (r/with-let [spec [::specs/note#history note-id]
               sub:history (-> *:store
                               (store/dispatch! [::res/submit! spec])
                               (store/subscribe [::res/?:resource spec]))
               sub:recon (store/subscribe *:store [:notes.history/?:reconstruction spec])]
    [comp/with-resource sub:history [note-history *:store @sub:recon]]
    (finally
      (store/emit! *:store [::res/destroyed spec]))))

(defn ^:private ->delete-modal [{note-id :notes/id}]
  [:modals/sure?
   {:description  "This note and all related schedules will be deleted"
    :yes-commands [[::res/submit!
                    [::specs/notes#destroy note-id]
                    {:ok-commands [[:nav/navigate! {:token :routes.ui/home}]]}]]}])

(defn ^:private ->edit-modal [{note-id :notes/id :as note}]
  [::note-edit/modal
   {:init         note
    :header       "Edit note"
    :params       {:prev-tags (:notes/tags note)
                   :ok-events [[::res/swapped [::specs/notes#find note-id]]
                               [::forms/created pin-note-key]]}
    :resource-key update-note-key}])

(defn ^:private root [*:store note]
  [:div.layout--stack-between
   [:div.layout--row
    [:h1.layout--space-after.flex-grow [:strong (:notes/context note)]]
    [pin-toggle *:store note]]
   [comp/markdown (:notes/body note)]
   [tag-list note]
   [:div.layout--space-between
    [:div.button-row
     [comp/plain-button {:*:store  *:store
                         :class    ["is-info"]
                         :commands [[:modals/create! (->edit-modal note)]]}
      "Edit"]
     [comp/plain-button {:*:store  *:store
                         :class    ["is-danger"]
                         :commands [[:modals/create! (->delete-modal note)]]}
      "Delete note"]]
    [comp/plain-button {:*:store  *:store
                        :class    ["is-light"]
                        :commands [[:modals/create! [::history {:note note}]]]}
     "View history"]]
   [sched.views/schedule-editor *:store note]])

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
            [comp/link {:token :routes.ui/home} "creating one"]
            "."]]

          :else
          [comp/spinner]))
      (finally
        (store/emit! *:store [::res/destroyed resource-key])))))
