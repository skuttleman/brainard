(ns brainard.ui.services.store.api
  (:require
    [brainard.common.navigation.core :as nav]
    [brainard.common.stubs.re-frame :as rf]
    [cljs-http.client :as http]
    [clojure.core.async :as async]
    [re-frame.core :as rf*]))

(def ^:private ^:const base-url "http://localhost:1165")

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 299)))

(rf*/reg-fx
  ::request
  (fn [{:keys [on-success-n on-error-n query-params] :as params}]
    (let [path (nav/path-for (:route params)
                             (:route-params params))
          query (nav/->query-string query-params)
          url (cond-> (str base-url path)
                query (str "?" query))]
      (async/go
        (let [request {:request-method (:method params)
                       :url            url
                       :body           (some-> (:body params) pr-str)
                       :headers        {"content-type" "application/edn"}}
              response (async/<! (http/request request))
              {:keys [data errors]} (:body response)]
          (if (success? (:status response))
            (run! (comp rf/dispatch #(conj % data)) on-success-n)
            (run! (comp rf/dispatch #(conj % errors)) on-error-n)))))))
