(ns brainard.infra.system.core
  (:require
    [brainard :as-alias b]
    [brainard.api.notifications.core :as notifications]
    [brainard.api.utils.logger :as log]
    [brainard.infra.db.store :as ds]
    [brainard.infra.obj.store :as os]
    [brainard.infra.routes.core :as routes]
    [brainard.infra.system.daemons :as daemons]
    [brainard.notifications.infra.manager :as manager]
    [immutant.web :as web]
    [integrant.core :as ig]
    brainard.attachments.infra.db
    brainard.attachments.infra.routes
    brainard.infra.routes.ui
    brainard.notes.infra.db
    brainard.notes.infra.routes
    brainard.notifications.infra.routes
    brainard.schedules.infra.db
    brainard.workspace.infra.db))

(defmethod ig/init-key :brainard.web/handler
  [_ {:keys [upload-limit]}]
  (fn [req]
    (routes/be-handler (assoc req ::b/file-limit-bytes upload-limit))))

(defmethod ig/init-key ::b/webserver
  [_ {:keys [apis handler server-port ws]}]
  (log/info "starting webserver on port" server-port)
  (web/run (fn [req]
             (handler (assoc req ::b/apis apis ::b/ws ws)))
           {:port server-port :host "0.0.0.0"}))

(defmethod ig/halt-key! ::b/webserver
  [_ server]
  (log/info "shutting down webserver")
  (web/stop server))

(defmethod ig/init-key ::b/storage
  [_ {:keys [conn]}]
  (ds/->DSStore conn))

(defmethod ig/init-key :brainard.ds/conn
  [_ input]
  (ds/connect! input))

(defmethod ig/init-key ::b/obj-storage
  [_ {:keys [invoker]}]
  (os/->ObjStore invoker))

(defmethod ig/init-key ::b/s3-invoker
  [_ params]
  (os/->invoke-fn params))

(defmethod ig/init-key :brainard.ws/manager
  [_ _]
  (manager/->NotificationManager (ref {})))

(defmethod ig/halt-key! :brainard.ws/manager
  [_ manager]
  (notifications/close! manager))

(defmacro ^:private thread-loop [interval & body]
  `(let [interval# (long ~interval)
         closed?# (volatile! false)]
     (fn []
       (try
         ~@body
         (Thread/sleep interval#)
         (catch Throwable ex#
           (vreset! closed?# true)
           (when (instance? InterruptedException ex#)
             (.interrupt (Thread/currentThread)))))
       (when-not @closed?#
         (recur)))))

(defmethod ig/init-key ::b/buzzer
  [_ {:keys [interval notes-api ws]}]
  (log/info "starting buzzer")
  (doto (Thread. ^Runnable (thread-loop interval
                             (daemons/update-buzz! notes-api ws)))
    .start))

(defmethod ig/halt-key! ::b/buzzer
  [_ thread]
  (log/info "stopping buzzer")
  (.interrupt thread))

(defmethod ig/init-key ::b/artifact-cleaner
  [_ {:keys [interval obj-store store]}]
  (log/info "starting orphaned artifact cleaner")
  (doto (Thread. ^Runnable (thread-loop interval
                             (daemons/cleanup-orphaned-artifacts! store obj-store)))
    .start))

(defmethod ig/halt-key! ::b/artifact-cleaner
  [_ thread]
  (log/info "stopping orphaned artifact cleaner")
  (.interrupt thread))
