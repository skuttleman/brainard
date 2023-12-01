(ns brainard.common.services.store.core
  (:require
    [re-frame.core :as rf]))

(def ^{:arglists '([query])} subscribe rf/subscribe)
(def ^{:arglists '([event])} dispatch rf/dispatch)
(def ^{:arglists '([event])} dispatch-sync rf/dispatch-sync)
