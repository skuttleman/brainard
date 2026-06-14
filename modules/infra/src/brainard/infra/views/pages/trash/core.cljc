(ns brainard.infra.views.pages.trash.core
  (:require
   [brainard.infra.store.core :as store]
   [brainard.infra.store.specs :as-alias specs]
   [brainard.infra.stubs.dom :as dom]
   [brainard.infra.views.components.core :as comp]
   [brainard.infra.views.pages.interfaces :as ipages]
   [brainard.infra.views.pages.trash.actions :as trash.act]
   [brainard.notes.infra.views :as notes.views]
   [defacto.forms.core :as-alias forms]
   [defacto.resources.core :as res]
   [whet.utils.reagent :as r]))



(defn ^:private ->delete-modal [note-ids]
  [:modals/sure?
   {:description   [:span "All archived notes will be " [:strong "permanently"] " deleted. Are you sure?"]
    :yes-btn-class ["note__confirm-delete"]
    :yes-commands  [[::res/resubmit!
                     trash.act/sync-key
                     {::trash.act/action ::trash.act/bulk-delete
                      :payload           {:notes/ids note-ids}
                      :ok-commands       [[:toasts/succeed! {:message "recycling bin emptied"}]]
                      :err-commands      [[:toasts/fail!]]}]]}])

(defn ^:private delete-button [*:store note-ids]
  [comp/plain-button {:*:store  *:store
                      :class    ["has-text-danger" "is-ghost" "space--left" "note__delete-button"]
                      :style    {:justify-content :flex-start
                                 :padding         0}
                      :on-click dom/stop-propagation!
                      :commands [[:modals/create! (->delete-modal note-ids)]]}
   "empty recycle bin"])

(defmethod ipages/page :routes.ui/trash
  [*:store _]
  (r/with-let [sub:res (store/res-sub *:store
                                      trash.act/sync-key
                                      {::trash.act/action ::trash.act/fetch
                                       :params            {:archived :only}})]
    (let [res @sub:res]
      [:div
       [:div.layout--space-between
        [:h2.subtitle "Archived notes"]
        (when (and (res/success? res) (seq (res/payload res)))
          [delete-button *:store (into #{} (map :notes/id) (res/payload res))])]
       [comp/with-resource sub:res [notes.views/note-list {:*:store *:store}]]])
    (finally
      (store/emit! *:store [::res/destroyed trash.act/sync-key]))))
