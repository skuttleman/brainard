(ns brainard.dev
  (:require
    [brainard :as-alias b]
    [brainard.api.utils.logger :as log]
    [brainard.core :as core]
    [brainard.infra.routes.core :as routes]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [duct.core :as duct]
    [integrant.core :as ig]
    [nrepl.server :as nrepl]
    [ring.middleware.reload :as ring.rel])
  (:import (java.io File)))

(defonce system nil)

(defn -main
  "Entry point for building/running the `brainard` web application from the command line.
   Runs an nREPL when `NREPL_PORT` env var is set."
  [& _]
  (let [nrepl-port (some-> (System/getenv "NREPL_PORT") Long/parseLong)]
    (when-let [nrepl-server (when nrepl-port
                              (log/info "starting nREPL server on port" nrepl-port)
                              (nrepl/start-server :bind "0.0.0.0" :port nrepl-port))]
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. ^Runnable
                                 (fn []
                                   (log/info "stopping nREPL server")
                                   (nrepl/stop-server nrepl-server)))))
    (alter-var-root #'system (constantly (core/start! "duct/dev.edn"
                                                      [:duct.profile/base :duct.profile/dev])))
    (duct/await-daemons system)))

(defmethod ig/init-key :brainard.web/dev-handler
  [_ {:keys [upload-limit]}]
  (-> #'routes/be-handler
      ((fn [handler] (fn [req] (handler (assoc req
                                               ::b/env :dev
                                               ::b/file-limit-bytes upload-limit)))))
      (ring.rel/wrap-reload {:dirs ["../defacto"
                                    "../whet"
                                    "modules/api/src"
                                    "modules/infra/src"
                                    "src"
                                    "dev"]})))

(defmulti ^:private fs-invoker :op)

(defmethod fs-invoker :GetObject
  [req]
  (let [{:keys [Key]} (:request req)
        content-type-file (str "target/" Key ".content-type")
        blob-file (str "target/" Key ".blob")
        ^File file (io/file blob-file)]
    {:Body          (io/input-stream file)
     :ContentLength (.length file)
     :ContentType   (slurp content-type-file)}))

(defmethod fs-invoker :PutObject
  [req]
  (let [{:keys [Body Key ContentType]} (:request req)
        content-type-file (str "target/" Key ".content-type")
        blob-file (str "target/" Key ".blob")]
    (io/make-parents content-type-file)
    (spit content-type-file ContentType)
    (io/copy Body (io/file blob-file))
    nil))

(defmethod fs-invoker :ListObjectsV2
  [_]
  {:Contents (->> (file-seq (io/file "target"))
                  (remove #(.isDirectory %))
                  (map #(-> %
                            .getName
                            (string/replace #"^target/" "")
                            (string/replace #"\..*$" "")
                            (->> (hash-map :Key))))
                  distinct)})

(defmethod fs-invoker :DeleteObjects
  [req]
  (doseq [key (->> req :request :Delete :Objects (map :Key))]
    (io/delete-file (io/file (format "target/%s.blob" key)))
    (io/delete-file (io/file (format "target/%s.content-type" key))))
  {})

(defmethod ig/init-key :brainard/fs-invoker
  [_ _]
  fs-invoker)

(comment
  (alter-var-root #'system (fn [sys]
                             (some-> sys ig/halt!)
                             (core/start! "duct/dev.edn" [:duct.profile/base :duct.profile/dev])))
  (ig/halt! system))
