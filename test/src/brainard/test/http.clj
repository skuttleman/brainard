(ns brainard.test.http
  (:require
    [brainard.infra.utils.edn :as edn]
    [brainard.infra.routes.core :as routes]
    [clojure.set :as set])
  (:import
    (java.io StringReader)
    (org.apache.commons.io.input NullReader)))

(defn request [req apis]
  (-> req
      (assoc :brainard/apis apis)
      (update :body #(if (some? %)
                       (StringReader. (pr-str %))
                       (NullReader/nullReader)))
      (set/rename-keys {:method :request-method})
      routes/be-handler
      (update :body edn/read-string)))

(defn success? [response]
  (<= 200 (:status response) 399))

(defn client-error? [response]
  (<= 400 (:status response) 499))
