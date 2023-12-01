(ns brainard.common.views.components.toasts
  (:require
    [brainard.common.services.store.core :as store]
    [brainard.common.stubs.reagent :as r]
    [clojure.core.async :as async]))

(defn ^:private level->class [level]
  (case level
    :success "is-success"
    :error "is-danger"
    :warning "is-warning"
    "is-info"))

(defn ^:private open-toast! [{toast-id :id :as toast}]
  (when (= :init (:state toast))
    (store/dispatch [:toasts/show toast-id]))
  (async/go
    (async/<! (async/timeout 5555))
    (store/dispatch [:toasts/hide toast-id]))
  toast)

(defn ^:private toast-message [{toast-id :id :as toast}]
  (r/with-let [height (volatile! nil)]
    (let [{:keys [body level state]} toast
          adding? (= state :init)
          removing? (= state :hidden)
          height-val @height]
      [:li.toast-message.message
       (cond-> {:ref   (fn [node]
                         (some->> node
                                  .getBoundingClientRect
                                  .-height
                                  (vreset! height)))
                :class [(level->class level)
                        (when adding? "adding")
                        (when removing? "removing")]}
         (and removing? height-val) (update :style assoc :margin-top (str "-" height-val "px")))
       [:div.message-body
        {:on-click (fn [_]
                     (store/dispatch [:toasts/hide toast-id]))
         :style    {:cursor :pointer}}
        [:div.body-text body]]])))

(defn toasts []
  (r/with-let [sub:toasts (store/subscribe [:toasts/toasts])]
    [:div.toast-container
     [:ul.toast-messages
      (for [toast @sub:toasts]
        ^{:key (:id toast)}
        [toast-message (open-toast! toast)])]]))
