(ns brainard.common.services.store.toasts
  "The life cycle for a toast is
   :init -> :visible -> [timeout or user interaction] -> :hidden -> (removed)"
  (:require
    [clojure.core.async :as async]
    [clojure.string :as string]
    [re-frame.core :as rf]))

(defn create
  "Creates a toast to be displayed as notifications in the UI. Toast wil be
   initially hidden."
  [{toast-id :toasts/id :keys [db]} [_ level body]]
  {:db    (assoc-in db [:toasts/toasts toast-id] {:state :init
                                                  :level level
                                                  :body  body})})

(defn show
  "Transitions a toast from :init -> :visible."
  [db [_ toast-id]]
  (cond-> db
    (get-in db [:toasts/toasts toast-id :state])
    (assoc-in [:toasts/toasts toast-id :state] :visible)))

(defn hide
  "Transitions a toast from :visible -> :hidden."
  [{:keys [db]} [_ toast-id]]
  (cond-> {::destroy {:toast-id toast-id}}
    (get-in db [:toasts/toasts toast-id])
    (assoc :db (assoc-in db [:toasts/toasts toast-id :state] :hidden))))

(defn destroy-fx
  "Triggers cleanup of :hidden toast from the store."
  [{:keys [toast-id]}]
  (async/go
    (async/<! (async/timeout 650))
    (rf/dispatch [:toasts/destroy toast-id])))

(defn destroy
  "Removes toast from the store."
  [db [_ toast-id]]
  (update db :toasts/toasts dissoc toast-id))

(defn on-success
  "Creates a toast from successful actions."
  [_ [_ {:keys [message]}]]
  {:dispatch [:toasts/create :success message]})

(defn on-failure
  "Creates a toast from unsuccessful actions."
  [_ [_ errors]]
  (let [msg (if-let [messages (seq (keep :message errors))]
              (string/join ", " messages)
              "An unknown error occurred")]
    {:dispatch [:toasts/create :error msg]}))
