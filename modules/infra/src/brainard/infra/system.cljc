(ns brainard.infra.system
  (:require
    #?@(:clj [[brainard.infra.obj.store :as os]
              [immutant.web :as web]
              brainard.infra.routes.ui])
    [brainard :as-alias b]
    [brainard.api.utils.logger :as log]
    [brainard.infra.db.store :as ds]
    [brainard.infra.routes.core :as routes]
    [integrant.core :as ig]
    brainard.attachments.infra.db
    brainard.attachments.infra.routes
    brainard.notes.infra.db
    brainard.notes.infra.routes
    brainard.schedules.infra.db
    brainard.workspace.infra.db))

#?(:clj
   (defmethod ig/init-key :brainard.web/handler
     [_ {:keys [upload-limit]}]
     (fn [req]
       (routes/be-handler (assoc req ::b/file-limit-bytes upload-limit)))))

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

(defmethod ig/init-key :brainard/storage
  [_ {:keys [conn]}]
  (ds/->DSStore conn))

(defmethod ig/init-key :brainard.ds/conn
  [_ input]
  (ds/connect! input))

#?(:clj
   (defmethod ig/init-key :brainard/obj-storage
     [_ {:keys [invoker]}]
     (os/->ObjStore invoker)))

#?(:clj
   (defmethod ig/init-key :brainard/s3-invoker
     [_ params]
     (os/->invoke-fn params)))
