(ns brainard.infra.views.components.toasts
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.reagent :as r]
    [clojure.core.async :as async]))

(defn ^:private level->class [level]
  (case level
    :success "is-success"
    :error "is-danger"
    :warning "is-warning"
    "is-info"))

(defn ^:private open-toast! [*:store {toast-id :id :as toast}]
  (when (= :init (:state toast))
    (store/emit! *:store [:toasts/shown toast-id]))
  (async/go
    (async/<! (async/timeout 4444))
    (store/dispatch! *:store [:toasts/hide! toast-id]))
  toast)

(defn ^:private toast-message [*:store {toast-id :id :as toast}]
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
       [:div.message-body.pointer
        {:on-click (fn [_]
                     (store/dispatch! *:store [:toasts/hide! toast-id]))}
        [:div.body-text body]]])))

(defn root [*:store]
  (r/with-let [sub:toasts (store/subscribe *:store [:toasts/?:toasts])]
    [:div.toast-container
     [:ul.toast-messages
      (for [toast @sub:toasts]
        ^{:key (:id toast)}
        [toast-message *:store (open-toast! *:store toast)])]]))
