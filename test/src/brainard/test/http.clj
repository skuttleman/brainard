(ns brainard.test.http
  (:require
    [brainard.api.utils.fns :as fns]
    [brainard.infra.utils.edn :as edn]
    [brainard.infra.routes.core :as routes]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.string :as string]
    [ring.util.mime-type :as mime])
  (:import
    (java.io InputStream StringReader)
    (org.apache.commons.io.input NullReader)))

(defn ^:private fixture->upload [filename]
  {:content-type (mime/ext-mime-type filename)
   :filename     (last (string/split filename #"/"))
   :stream       (-> filename
                     io/resource
                     io/input-stream)})

(defn request [req apis]
  (let [body-str (pr-str (:body req))
        response (-> req
                     (assoc :brainard/apis apis)
                     (set/rename-keys {:method :request-method})
                     (cond->
                       (:body req)
                       (-> (assoc :body (StringReader. body-str))
                           (update :headers assoc
                                   "content-type" "application/edn"
                                   "content-length" (str (count body-str))))

                       (:multipart-params req)
                       (update-in [:multipart-params :files] fns/smap fixture->upload))
                     routes/be-handler)]
    (cond-> response
      (= "application/edn" (get-in response [:headers "content-type"]))
      (update :body edn/read-string)

      (instance? InputStream (:body response))
      (update :body slurp))))

(defn success? [response]
  (<= 200 (:status response) 399))

(defn client-error? [response]
  (<= 400 (:status response) 499))
