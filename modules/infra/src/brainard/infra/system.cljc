(ns brainard.infra.system
  (:require
    #?@(:clj [[immutant.web :as web]
              brainard.infra.routes.ui])
    [brainard.api.utils.logger :as log]
    [brainard.infra.db.datascript :as ds]
    [brainard.infra.routes.core :as routes]
    [brainard.notes.infra.db :as stores.notes]
    [brainard.schedules.infra.db :as stores.sched]
    [brainard.workspace.infra.db :as stores.work]
    [integrant.core :as ig]
    brainard.notes.infra.routes
    brainard.schedules.infra.routes))

#?(:clj
   (defmethod ig/init-key :brainard.web/handler
     [_ _]
     routes/be-handler))

#?(:clj
   (defmethod ig/init-key :brainard/webserver
     [_ {:keys [apis handler server-port]}]
     (log/info "starting webserver on port" server-port)
     (web/run (fn [req]
                (handler (assoc req :brainard/apis apis)))
              {:port server-port :host "0.0.0.0"})))

#?(:clj
   (defmethod ig/halt-key! :brainard/webserver
     [_ server]
     (log/info "shutting down webserver")
     (web/stop server)))

(defmethod ig/init-key :brainard.ds/storage-logger
  [_ params]
  #?(:clj  (ds/file-logger (:db-name params))
     :cljs (ds/local-storage-logger (:db-name params))))

(defmethod ig/init-key :brainard.ds/client
  [_ {:keys [logger]}]
  (doto (ds/connect! logger)
    ds/init!))

(defmethod ig/halt-key! :brainard.ds/client
  [_ conn]
  (ds/close! conn))

(defmethod ig/init-key :brainard.stores/notes
  [_ deps]
  (stores.notes/create-store deps))

(defmethod ig/init-key :brainard.stores/schedules
  [_ deps]
  (stores.sched/create-store deps))

(defmethod ig/init-key :brainard.stores/workspace
  [_ deps]
  (stores.work/create-store deps))
