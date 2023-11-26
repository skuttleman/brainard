(ns brainard.common.views.toasts
  (:require
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.stubs.reagent :as r]))

(defn level->class [level]
  (case level
    :success "is-success"
    :error "is-danger"
    :warning "is-warning"
    "is-info"))

(defn ^:private toast-message [toast-id toast]
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
                     (rf/dispatch [:toasts/hide toast-id]))
         :style    {:cursor :pointer}}
        [:div.body-text body]]])))

(defn toasts []
  (let [sub:toasts (rf/subscribe [:toasts/toasts])]
    (fn []
      [:div.toast-container
       [:ul.toast-messages
        (for [[toast-id toast] @sub:toasts]
          ^{:key toast-id}
          [toast-message toast-id toast])]])))

