(ns brainard.infra.system.core
  (:require
    [aleph.http :as http]
    [brainard :as-alias b]
    [brainard.api.events.core :as events]
    [brainard.api.utils.logger :as log]
    [brainard.events.infra.manager :as manager]
    [brainard.infra.db.store :as ds]
    [brainard.infra.obj.store :as os]
    [brainard.infra.routes.core :as routes]
    [brainard.infra.system.daemons :as daemons]
    [integrant.core :as ig]
    brainard.attachments.infra.db
    brainard.attachments.infra.routes
    brainard.infra.routes.ui
    brainard.notes.infra.db
    brainard.notes.infra.routes
    brainard.events.infra.routes
    brainard.schedules.infra.db
    brainard.workspace.infra.db)
  (:import
    (java.util Date)))

(defmethod ig/init-key :brainard.web/handler
  [_ {:keys [env ui-env upload-limit] :as cfg}]
  (when-not (and env (int? upload-limit))
    (throw (ex-info "handler requires env and upload-limits" cfg)))
  (fn [req]
    (routes/be-handler (assoc req
                              ::b/env env
                              ::b/ui-env ui-env
                              ::b/file-limit-bytes upload-limit))))

(defmethod ig/init-key ::b/webserver
  [_ {:keys [apis events handler server-port]}]
  (log/info "starting webserver on port" server-port)
  (http/start-server (fn [req]
                       (handler (assoc req ::b/apis apis ::b/events events)))
                     {:port server-port}))

(defmethod ig/halt-key! ::b/webserver
  [_ server]
  (log/info "shutting down webserver")
  (.close server))

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

(defmethod ig/init-key :brainard/events
  [_ _]
  (manager/->EventsManager (ref {})))

(defmethod ig/halt-key! :brainard/events
  [_ manager]
  (events/close! manager))

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
  [_ {:keys [apis events interval]}]
  (log/info "starting buzzer")
  (doto (Thread. ^Runnable (thread-loop interval
                             (daemons/update-buzz! apis events (Date.))))
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
