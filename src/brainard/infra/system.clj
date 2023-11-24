(ns brainard.infra.system
  (:require
    [brainard.infra.datomic :as datomic]
    [brainard.infra.stores.notes :as stores.notes]
    [duct.core :as duct]
    [brainard.infra.routes.core :as routes]
    [immutant.web :as web]
    [integrant.core :as ig]
    [taoensso.timbre :as log]))

(defmethod ig/init-key :brainard.datomic/client
  [_ params]
  (doto (datomic/create-client (:client params))
    (datomic/create-database (:db-name params))))

(defmethod ig/init-key :brainard.datomic/conn
  [_ {:keys [client db-name schema-file]}]
  (doto (datomic/connect! client db-name)
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

(defn start! [config-file]
  (duct/load-hierarchy)
  (-> config-file
      duct/resource
      duct/read-config
      (duct/prep-config [:duct.profile/base :duct.profile/prod])
      (ig/init [:duct/daemon])))
