(ns brainard.infra.views.components.modals
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.stubs.reagent :as r]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.components.shared :as scomp]))

(defn modal* [*:store idx {modal-id :id :as modal}]
  (letfn [(close! [_]
            (store/dispatch! *:store [:modals/remove! modal-id]))]
    (r/with-let [stop-and-close! (comp close! dom/stop-propagation!)
                 listener (dom/add-listener! dom/window
                                             :keydown
                                             #(when (#{:key-codes/esc} (dom/event->key %))
                                                (stop-and-close! %))
                                             true)
                 [modal-type attrs] (:body modal)
                 attrs (assoc attrs :close! close! :modals/type modal-type)]
      (let [inset (str (* 8 idx) "px")]
        [:li.modal-item
         {:class    [(case (:state modal)
                       :init "adding"
                       :hidden "removing"
                       nil)]
          :style    {:padding-left inset
                     :padding-top  inset}
          :on-click dom/stop-propagation!}
         [scomp/tile
          [:div.layout--space-between
           [:div [icomp/modal-header *:store attrs]]
           [scomp/plain-button
            {:class    ["is-white" "is-light"]
             :on-click stop-and-close!}
            [scomp/icon :times]]]
          [:div [icomp/modal-body *:store attrs]]]])
      (finally
        (dom/remove-listener! listener)))))

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
             [modal* *:store idx modal])]]]))))
