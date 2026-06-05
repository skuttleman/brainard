(ns brainard.api.notifications.interfaces)

(defprotocol IConnect
  "Connectable Resource"
  :extend-via-metadata true
  (connect! [this ch-id ch]
    "Connect a channel to the resource")
  (close! [this]
    "Close all channels")
  (disconnect! [this ch-id]
    "Disconnect a chanel from the resource"))

(defprotocol ISend
  "Sends connected channels a given message"
  :extend-via-metadata true
  (broadcast! [this msg]
    "Broadcasts a message to all connected channels"))
