(ns brainard.ui.services.store.toasts
  (:require
    [brainard.common.stubs.re-frame :as rf]
    [clojure.core.async :as async]
    [clojure.string :as string]
    [re-frame.core :as rf*]))

(rf*/reg-fx
  ::destroy
  (fn [{:keys [toast-id]}]
    (async/go
      (async/<! (async/timeout 650))
      (rf/dispatch [:toasts/destroy toast-id]))))

(defn show [db [_ toast-id]]
  (cond-> db
    (get-in db [:toasts/toasts toast-id :state])
    (assoc-in [:toasts/toasts toast-id :state] :visible)))

(defn destroy [db [_ toast-id]]
  (update db :toasts/toasts dissoc toast-id))

(defn create [{toast-id :toasts/id :keys [db]} [_ level body]]
  {:db (assoc-in db [:toasts/toasts toast-id] {:state :init
                                        :level level
                                        :body  body})})

(defn hide [{:keys [db]} [_ toast-id]]
  (cond-> {::destroy {:toast-id toast-id}}
    (get-in db [:toasts/toasts toast-id])
    (assoc :db (assoc-in db [:toasts/toasts toast-id :state] :hidden))))

(defn on-success [_ [_ {:keys [message]}]]
  {:dispatch [:toasts/create :success message]})

(defn on-failure [_ [_ errors]]
  (let [msg (if-let [messages (seq (keep :message errors))]
              (string/join ", " messages)
              "An unknown error occurred")]
    {:dispatch [:toasts/create :error msg]}))
