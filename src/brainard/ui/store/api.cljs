(ns brainard.ui.store.api
  (:require
    [bidi.bidi :as bidi]
    [brainard.common.routing :as routing]
    [cljs-http.client :as http]
    [clojure.core.async :as async]
    [re-frame.core :as rf]))

(def ^:private ^:const base-url "http://localhost:1165")

(rf/reg-event-db
  ::success
  (fn [db [_ db-key data]]
    (assoc db db-key [:success data])))

(rf/reg-event-db
  ::error
  (fn [db [_ db-key error]]
    (assoc db db-key [:error error])))

(rf/reg-fx
  ::request
  (fn [{:keys [on-success on-error] :as params}]
    (let [path (bidi/path-for routing/api-routes
                              (:route params)
                              (:route-params params {}))
          url (str base-url path)]
      (async/go
        (let [response (async/<! (http/request {:request-method (:method params)
                                                :url            url
                                                :body           (some-> (:body params) pr-str)
                                                :headers        {"content-type" "application/edn"}}))]
          (if (<= 200 (:status response) 299)
            (some-> on-success (conj (-> response :body :data)) rf/dispatch)
            (some-> on-error (conj (-> response :body :errors)) rf/dispatch)))))))
