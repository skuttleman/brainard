(ns brainard.common.resources.api
  (:require
    [brainard.common.store.core :as store]
    [clojure.core.async :as async]
    [defacto.core :as defacto]))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 299)))

(defn ^:private send-all [send-fn messages result]
  (run! send-fn (for [msg messages]
                  (conj msg result))))

(defmethod defacto/command-handler ::request!
  [{::defacto/keys [store] :services/keys [http]} [_ params] emit-cb]
  (let [{:keys [req pre-events pre-commands ok-events ok-commands err-events err-commands]} params]
    (run! emit-cb pre-events)
    (run! (partial store/dispatch! store) pre-commands)
    (#?(:cljs async/go :default do)
      (let [response (#?(:cljs async/<! :default do) (http req))
            {:keys [data errors]} (:body response)
            payload (or errors data)
            [events commands] (if (success? (:status response))
                                [ok-events ok-commands]
                                [err-events err-commands])]
        (send-all emit-cb events payload)
        (send-all (partial store/dispatch! store) commands payload)))))