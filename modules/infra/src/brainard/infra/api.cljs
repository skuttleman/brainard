(ns brainard.infra.api
  (:require
    [brainard.common.routes.base :as base]
    [brainard.infra.db.datascript :as ds]
    [brainard.notes.infra.db :as notes]
    [brainard.schedules.infra.db :as sched]
    [cljs-http.client :as http]
    [clojure.core.async :as async]
    [defacto.resources.core :as-alias res]
    brainard.common.routes.notes
    brainard.common.routes.schedules))

(defonce ^:private apis
  (delay (let [conn (doto (ds/connect! {}) ds/init!)]
           {:notes     {:store (notes/create-store {:datascript-conn conn})}
            :schedules {:store (sched/create-store {:datascript-conn conn})}})))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 299)))

(defn ^:private merger [row1 row2]
  (if (and (map? row1) (map? row2))
    (merge-with into row1 row2)
    (or row2 row1)))

(defn ^:private remote->warnings [warnings]
  (transduce (map :details) merger nil warnings))

(defn ^:private do-request [params]
  (async/go
    #_(base/ui-handler (assoc params :brainard/apis @apis))
    (async/<! (http/request params))))

(defn request-fn [_ params]
  (async/go
    (let [{:keys [status body]} (async/<! (do-request params))]
      (if (success? status)
        [::res/ok (:data body)]
        [::res/err (remote->warnings (:errors body))]))))
