(ns brainard.infra.views.components.modals
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.components.shared :as scomp]
    [whet.utils.reagent :as r]))

(defn ^:private close-modal! [*:store modal-id]
  (fn [_]
    (store/dispatch! *:store [:modals/remove! modal-id])))

(defn ^:private modal-tile [*:store attrs stop-and-close!]
  [scomp/tile
   [:div.layout--space-between
    [:div [icomp/modal-header *:store attrs]]
    [scomp/plain-button
     {:class    ["is-white" "is-light"]
      :on-click stop-and-close!}
     [scomp/icon :close]]]
   [:div {:style {:max-width  "80vw"
                  :max-height "80vh"
                  :overflow-y :scroll}}
    [icomp/modal-body *:store attrs]]])

(defn modal-view [*:store idx {modal-id :id :as modal}]
  (r/with-let [close! (close-modal! *:store modal-id)
               stop-and-close! (comp close! dom/stop-propagation!)
               listener (dom/add-listener! dom/window
                                           :keydown
                                           #(when (#{:key-codes/esc} (dom/event->key %))
                                              (stop-and-close! %))
                                           true)
               [modal-type attrs] (:body modal)
               attrs (assoc attrs :close! close! :modals/type modal-type :modals/id modal-id)]
    (let [inset (str (* 8 idx) "px")]
      [:li.modal-item
       {:class    [(case (:state modal)
                     :init "adding"
                     :hidden "removing"
                     nil)]
        :style    {:padding-left inset
                   :padding-top  inset}
        :on-click dom/stop-propagation!}
       [modal-tile *:store attrs stop-and-close!]])
    (finally
      (dom/remove-listener! listener))))

(defn root [*:store]
  (r/with-let [sub:modals (store/subscribe *:store [:modals/?:modals])]
    (let [modals @sub:modals
          active? (and (seq modals)
                       (or (seq (rest modals))
                           (not= :removing (:state (first modals)))))]
      (when (seq modals)
        [:div.modal-container
         {:class    [(when active? "is-active")]
          :on-click (when active?
                      (fn [_]
                        (store/dispatch! *:store [:modals/remove-all!])))}
         [:div.modal-stack
          [:ul.modal-list
           (for [[idx modal] (map-indexed vector modals)]
             ^{:key (:id modal)}
             [modal-view *:store idx modal])]]]))))
