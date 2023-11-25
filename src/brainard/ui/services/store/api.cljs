(ns brainard.ui.services.store.api
  (:require
    [bidi.bidi :as bidi]
    [brainard.common.routing :as routing]
    [brainard.common.stubs.re-frame :as rf]
    [cljs-http.client :as http]
    [clojure.core.async :as async]
    [re-frame.core :as rf*]))

(def ^:private ^:const base-url "http://localhost:1165")

(rf*/reg-event-db
  ::success
  (fn [db [_ path data]]
    (assoc-in db path [:success data])))

(rf*/reg-event-db
  ::error
  (fn [db [_ path error]]
    (assoc-in db path [:error error])))

(rf*/reg-fx
  ::request
  (fn [{:keys [on-success-n on-error-n] :as params}]
    (let [path (bidi/path-for routing/api-routes
                              (:route params)
                              (:route-params params {}))
          url (str base-url path)]
      (async/go
        (let [request {:request-method (:method params)
                       :url            url
                       :body           (some-> (:body params) pr-str)
                       :headers        {"content-type" "application/edn"}}
              response (async/<! (http/request request))
              {:keys [data errors]} (:body response)]
          (if (<= 200 (:status response) 299)
            (run! (comp rf/dispatch #(conj % data)) on-success-n)
            (run! (comp rf/dispatch #(conj % errors)) on-error-n)))))))
