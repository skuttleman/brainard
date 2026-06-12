(ns brainard.infra.views.components.toasts
  "Page-level component for displaying toast messages."
  (:require
   #?(:cljs [slag.utils.edn :as edn])
   [brainard.infra.store.core :as store]
   [clojure.core.async :as async]
   [whet.utils.reagent :as r]))

(defmulti ^{:arglists '([body])} toast-body
          "Used to display component trees"
          (fn [body] (when (vector? body) (first body))))
(defmethod toast-body :default [body] body)

(def ^:const ^:private TOAST_TIMEOUT
  #?(:cljs    (edn/read-string (.-TOAST_TIMEOUT js/window))
     :default nil))

(defn ^:private level->class [level]
  (case level
    :success "is-success"
    :error "is-danger"
    :warning "is-warning"
    "is-info"))

(defn ^:private open-toast! [*:store {toast-id :id :as toast timeout :timeout}]
  (when (= :init (:state toast))
    (store/emit! *:store [:toasts/shown toast-id]))
  (async/go
    (async/<! (async/timeout (or timeout TOAST_TIMEOUT 4500)))
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
        [:div.body-text [toast-body body]]]])))

(defn root [*:store]
  (r/with-let [sub:toasts (store/subscribe *:store [:toasts/?:toasts])]
    [:div.toast-container
     [:ul.toast-messages
      (for [toast @sub:toasts]
        ^{:key (:id toast)}
        [toast-message *:store (open-toast! *:store toast)])]]))
