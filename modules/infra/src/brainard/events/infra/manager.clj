(ns brainard.events.infra.manager
  (:require
    [brainard.api.events.interfaces :as ievents]
    [brainard.api.utils.logger :as log]
    [immutant.web.async :as web.async]))

(defn ^:private fmt-event [msg]
  (str "event: message" \newline
       "data: " (pr-str msg) \newline \newline))

(defn ^:private ->EventsManager [subs send-fn close-fn]
  (reify
    ievents/IConnect
    (connect! [_ ch-id ch]
      (dosync
        (alter subs assoc ch-id ch)))
    (close! [_]
      (doseq [ch (vals @subs)]
        (try
          (close-fn ch)
          (catch Throwable ex
            (log/warn ex "could not gracefully shutdown channel"))))
      (dosync
        (ref-set subs {})))
    (disconnect! [_ ch-id]
      (dosync
        (alter subs dissoc ch-id)))

    ievents/ISend
    (broadcast! [_ msg]
      (let [event (fmt-event msg)]
        (doseq [ch (vals @subs)]
          (send-fn ch event))))))

(defn create
  "Creates an EventsManager instance"
  ([]
   (create web.async/send! web.async/close))
  ([send-fn close-fn]
   (->EventsManager (ref {}) send-fn close-fn)))
