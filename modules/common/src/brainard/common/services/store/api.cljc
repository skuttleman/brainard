(ns brainard.common.services.store.api
  (:require
    #?(:cljs [cljs-http.client :as http])
    [brainard.common.services.navigation.core :as nav]
    [clojure.core.async :as async]
    [re-frame.core :as rf]))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 299)))

(rf/reg-fx
  ::request
  (fn [{:keys [on-success-n on-error-n query-params] :as params}]
    (let [path (nav/path-for (:route params)
                               (:route-params params))
            query (nav/->query-string query-params)
            url (cond-> path
                  query (str "?" query))]
       (async/go
         (let [request {:request-method (:method params)
                        :url            url
                        :body           (some-> (:body params) pr-str)
                        :headers        {"content-type" "application/edn"}}
               response #?(:cljs (async/<! (http/request request)) :default nil)
               {:keys [data errors]} (:body response)]
           (if (success? (:status response))
             (run! (comp rf/dispatch #(conj % data)) on-success-n)
             (run! (comp rf/dispatch #(conj % errors)) on-error-n)))))))
