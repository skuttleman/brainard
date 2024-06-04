(ns brainard.stubbed-be
  (:require
    [brainard :as-alias b]
    [brainard.infra.routes.core :as routes]
    [brainard.infra.utils.routing :as rte]
    [brainard.resources.system :as sys]
    [clojure.core.async :as async]
    [defacto.resources.core :as-alias res]
    [integrant.core :as ig]
    [whet.core :as-alias w]
    [whet.interfaces :as iwhet]
    [whet.utils.navigation :as nav]
    brainard.infra.system))

(def ^:private system
  (ig/init (sys/config) [::b/apis]))

(def ^:private apis
  (val (ig/find-derived-1 system ::b/apis)))

(defn ^:private handler [{{:keys [token route-params query-params]} :route :as req}]
  (let [uri (nav/path-for rte/all-routes token route-params query-params)
        route-info (nav/match rte/all-routes uri)]
    (routes/handler (assoc req ::b/apis apis ::w/route route-info))))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 299)))

(defmethod iwhet/handle-request :default
  [_ _ params]
  (async/go
    (let [{:keys [status body]} (handler params)]
      (if (success? status)
        [::res/ok body]
        [::res/err body]))))
