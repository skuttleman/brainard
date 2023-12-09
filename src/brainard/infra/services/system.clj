(ns brainard.infra.services.system
  (:require
    [brainard.common.utils.logger :as log]
    [brainard.infra.services.datomic :as datomic]
    [brainard.infra.stores.notes :as stores.notes]
    [duct.core :as duct]
    [brainard.infra.routes.core :as routes]
    [immutant.web :as web]
    [integrant.core :as ig]))

(defmethod ig/init-key :brainard.datomic/file-logger
  [_ params]
  (datomic/file-logger (:db-name params)))

(defmethod ig/init-key :brainard.datomic/client
  [_ {:keys [client db-name]}]
  (doto (datomic/create-client (assoc client :system db-name))
    (datomic/create-database db-name)))

(defmethod ig/init-key :brainard.datomic/conn
  [_ {:keys [client db-name logger schema-file]}]
  (doto (datomic/connect! client db-name logger)
    (datomic/init! schema-file)))

(defmethod ig/halt-key! :brainard.datomic/conn
  [_ conn]
  (datomic/close! conn))

(defmethod ig/init-key :brainard.store/notes
  [_ deps]
  (stores.notes/create-store deps))

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
