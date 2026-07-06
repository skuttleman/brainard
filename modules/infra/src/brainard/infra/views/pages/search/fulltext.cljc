(ns brainard.infra.views.pages.search.fulltext
  (:require
   [brainard.infra.store.core :as store]
   [brainard.infra.store.utils :as ustore]
   [brainard.infra.views.components.interfaces :as icomp]
   [brainard.infra.views.controls.core :as ctrls]
   [brainard.infra.views.fragments.actions :as-alias frag.act]
   [brainard.infra.views.pages.interfaces :as ipages]
   [defacto.resources.core :as-alias res]))

(def nav-search-key [::frag.act/notes#search ::nav])

(defmethod icomp/modal-header ::modal
  [_ _]
  "Search for a note")

(defmethod icomp/modal-body ::modal
  [*:store {modal-id :modals/id}]
  (store/with-let [sub:form (store/form-sub *:store [::search] nil)
                   sub:matches (store/res-init-sub *:store nav-search-key [])
                   on-select (fn [note]
                               (-> *:store
                                   (store/dispatch! [:nav/navigate!
                                                     {:token        :routes.ui/note
                                                      :route-params {:notes/id (:notes/id note)}}])
                                   (store/dispatch! [:modals/remove! modal-id])))]
    (let [form @sub:form]
      [ctrls/typeahead (-> {:*:store    *:store
                            :auto-focus true
                            :item-fn    :notes/summary
                            :key-fn     :notes/id
                            :on-select  on-select
                            :sub:items  sub:matches}
                           (ctrls/with-attrs form [::note])
                           (update :on-change
                                   (fn [event]
                                     {:command [::res/debounce! ::search 400 [::res/resubmit! nav-search-key]]
                                      :event   event})))])))

(defmethod ipages/kb-shortcut! [#{:ctrl} :key-codes/space]
  [*:store _]
  (when (empty? (store/query *:store [:modals/?:modals (ustore/modals-sans-state :hidden)]))
    (store/dispatch! *:store [:modals/create! [::modal {:style {:overflow-y :visible}}]])))
