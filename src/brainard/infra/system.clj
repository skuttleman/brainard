(ns brainard.infra.system
  (:require
    [brainard.common.utils.logger :as log]
    [brainard.infra.db.datascript :as ds]
    [brainard.infra.db.notes :as stores.notes]
    [brainard.infra.db.schedules :as stores.sched]
    [duct.core :as duct]
    [brainard.infra.routes.core :as routes]
    [immutant.web :as web]
    [integrant.core :as ig]))

(defmethod ig/init-key :brainard.datascript/file-logger
  [_ params]
  (ds/file-logger (:db-name params)))

(defmethod ig/init-key :brainard.datascript/conn
  [_ {:keys [logger]}]
  (doto (ds/connect! logger)
    ds/init!))

(defmethod ig/halt-key! :brainard.datascript/conn
  [_ conn]
  (ds/close! conn))

(defmethod ig/init-key :brainard.store/notes
  [_ deps]
  (stores.notes/create-store deps))

(defmethod ig/init-key :brainard.store/schedules
  [_ deps]
  (stores.sched/create-store deps))

(defmethod ig/init-key :brainard/webserver
  [_ {:keys [apis server-port]}]
  (log/info "starting webserver on port" server-port)
  (web/run (fn [req]
             (routes/handler (assoc req :brainard/apis apis)))
           {:port server-port :host "0.0.0.0"}))

(defmethod ig/halt-key! :brainard/webserver
  [_ server]
  (log/info "shutting down webserver")
  (web/stop server))

(defn start!
  "Starts a duct component system from a configuration expressed in an `edn` file."
  [config-file]
  (duct/load-hierarchy)
  (-> config-file
      duct/resource
      duct/read-config
      (duct/prep-config [:duct.profile/base :duct.profile/prod])
      (ig/init [:duct/daemon])))
