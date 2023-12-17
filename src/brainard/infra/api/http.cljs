(ns brainard.infra.api.http
  (:require
    [cljs-http.client :as http]
    [clojure.core.async :as async]
    [defacto.resources.core :as-alias res]))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 299)))

(defn ^:private merger [row1 row2]
  (if (and (map? row1) (map? row2))
    (merge-with conj row1 row2)
    (or row2 row1)))

(defn ^:private remote->warnings [warnings]
  (transduce (map :details) merger nil warnings))

(defn request-fn [_ params]
  (async/go
    (let [{:keys [status body]} (async/<! (http/request params))]
      (if (success? status)
        [::res/ok (:data body)]
        [::res/err (remote->warnings (:errors body))]))))
