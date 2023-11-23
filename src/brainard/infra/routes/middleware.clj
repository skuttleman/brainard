(ns brainard.infra.routes.middleware
  (:require
    [bidi.bidi :as bidi]
    [brainard.core.utils.edn :as edn]
    [brainard.infra.routes.table :as table]
    [ring.util.request :as ring.req]))

(defn with-routing [handler]
  (fn [req]
    (let [route-handler (bidi/match-route table/all (:uri req))]
      (handler (assoc req :brainard/route route-handler)))))

(defn with-edn [handler]
  (fn [req]
    (let [content-type (or (ring.req/content-type req)
                           "application/edn")
          response (-> req
                       (cond-> (= content-type "application/edn")
                               (update :body edn/read))
                       handler)]
      (cond-> response
        (and (nil? (get-in response [:headers "content-type"]))
             (some? (:body response)))
        (-> (assoc-in [:headers "content-type"] "application/edn")
            (update :body pr-str))))))
