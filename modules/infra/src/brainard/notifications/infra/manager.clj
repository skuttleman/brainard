(ns brainard.notifications.infra.manager
  (:require
    [brainard.api.notifications.interfaces :as inotifications]
    [immutant.web.async :as web.async]))

(defn ^:private fmt-event [msg]
  (str "event: message" \newline
       "data: " (pr-str msg) \newline \newline))

(deftype NotificationManager [subs]
  inotifications/IConnect
  (connect! [_ ch-id ch]
    (dosync
      (alter subs assoc ch-id ch)))
  (close! [_]
    (doseq [ch (vals @subs)]
      (web.async/close ch))
    (dosync
      (ref-set subs {})))
  (disconnect! [_ ch-id]
    (dosync
      (alter subs dissoc ch-id)))

  inotifications/ISend
  (broadcast! [_ msg]
    (let [event (fmt-event msg)]
      (doseq [ch (vals @subs)]
        (web.async/send! ch event)))))
