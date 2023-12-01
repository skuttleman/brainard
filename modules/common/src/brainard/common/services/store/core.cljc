(ns brainard.common.services.store.core
  (:require
    [re-frame.core :as rf]))

(def ^{:arglists '([query])} subscribe
  "Subscribes to changes in store data."
  rf/subscribe)

(def ^{:arglists '([event])} dispatch
  "Dispatches events that flow through the store."
  rf/dispatch)

(def ^{:arglists '([event])} dispatch-sync
  "Dispatches events synchronously that flow through the store.
   Prefer [[dispatch]] for things other than `on-change` handlers, etc."
  rf/dispatch-sync)
