(ns brainard.common.store.api
  (:require
    #?(:cljs [cljs-http.client :as http])
    [brainard.common.utils.routing :as rte]
    [brainard.common.store.core :as store]
    [clojure.core.async :as async]))

(def ^:dynamic *request-fn*
  #?(:cljs    http/request
     :default (constantly (async/chan))))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 299)))

(defn request!
  "Sends HTTP request and returns a core.async channel which dispatches
   `on-success-n` or `on-error-n` events with the corresponding result or errors."
  [store {:keys [on-success-n on-error-n] :as params}]
  (let [url (rte/path-for (:route params) (:params params))]
    (async/go
      (let [request {:request-method (:method params)
                     :url            url
                     :body           (some-> (:body params) pr-str)
                     :headers        {"content-type" "application/edn"}}
            response (async/<! (*request-fn* request))
            {:keys [data errors]} (:body response)]
        (if (success? (:status response))
          (run! (comp (partial store/dispatch! store) #(conj % data)) on-success-n)
          (run! (comp (partial store/dispatch! store) #(conj % errors)) on-error-n))
        nil))))
