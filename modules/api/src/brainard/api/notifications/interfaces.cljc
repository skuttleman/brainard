(ns brainard.api.notifications.interfaces)

(defprotocol IConnect
  ""
  :extend-via-metadata true
  (connect! [this ch-id ch]
    "")
  (close! [this]
    "")
  (disconnect! [this ch-id]
    ""))

(defprotocol ISend
  ""
  :extend-via-metadata true
  (broadcast! [this msg]
    ""))
