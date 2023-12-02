(ns brainard.common.services.store.api
  (:require
    #?(:cljs [cljs-http.client :as http])
    [brainard.common.utils.routing :as rte]
    [clojure.core.async :as async]
    [re-frame.core :as rf]))

(def ^:dynamic *request-fn*
  #?(:cljs    http/request
     :default (constantly (async/chan))))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 299)))

(defn request-fx
  "Sends HTTP request and returns a core.async channel which dispatches
   `on-success-n` or `on-error-n` events with the corresponding result or errors."
  [{:keys [on-success-n on-error-n query-params] :as params}]
  (let [path (rte/path-for (:route params)
                           (:route-params params))
        query (rte/->query-string query-params)
        url (cond-> path
              query (str "?" query))]
    (async/go
      (let [request {:request-method (:method params)
                     :url            url
                     :body           (some-> (:body params) pr-str)
                     :headers        {"content-type" "application/edn"}}
            response (async/<! (*request-fn* request))
            {:keys [data errors]} (:body response)]
        (if (success? (:status response))
          (run! (comp rf/dispatch #(conj % data)) on-success-n)
          (run! (comp rf/dispatch #(conj % errors)) on-error-n))
        nil))))
