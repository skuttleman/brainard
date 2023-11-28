(ns brainard.ui.services.store.api
  (:require
    [bidi.bidi :as bidi]
    [brainard.common.routing :as routing]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.utils.keywords :as kw]
    [cljs-http.client :as http]
    [clojure.core.async :as async]
    [clojure.string :as string]
    [re-frame.core :as rf*]))

(def ^:private ^:const base-url "http://localhost:1165")

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 299)))

(defn ^:private ->query [query-params]
  (->> query-params
       (mapcat (fn [[k v]]
                 (when (some? v)
                   (map (fn [v']
                          (str (name k) "=" (cond-> v' (keyword? v') kw/kw-str)))
                        (cond-> v (not (coll? v)) vector)))))
       (string/join "&")))

(rf*/reg-fx
  ::request
  (fn [{:keys [on-success-n on-error-n query-params] :as params}]
    (let [path (bidi/path-for routing/api-routes
                              (:route params)
                              (:route-params params {}))
          query (->query query-params)
          url (cond-> (str base-url path)
                (seq query-params) (str "?" query))]
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
