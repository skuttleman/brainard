(ns brainard.infra.routes.middleware
  (:require
   #?@(:clj [[brainard.infra.routes.errors :as routes.err]
             [brainard.api.utils.logger :as log]
             [clojure.string :as string]
             [slag.utils.maps :as maps]])
   [brainard.infra.routes.interfaces :as iroutes]))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 399)))

(defn with-input
  "Includes route input as :brainard/input via [[iroutes/req->input]]"
  [handler]
  (fn [req]
    (handler (assoc req :brainard/input (iroutes/req->input req)))))

#?(:clj
   (defn ^:private log-req [{:keys [ex result duration]} {:keys [uri] :as req}]
     (let [status (:status result)
           duration (str "["
                         (cond
                           (> duration 500) (log/red (str duration "ms"))
                           (> duration 100) (log/yellow (str duration "ms"))
                           (> duration 50) (log/blue (str duration "ms"))
                           :else (str duration "ms"))
                         "]:")
           method (string/upper-case (name (:request-method req)))]
       (if (and (nil? ex) (success? status))
         (log/info method uri duration status)
         (log/error method uri duration status)))))

#?(:clj
   (defn with-logging
     "Logs request/response details."
     ([handler]
      (with-logging handler nil))
     ([handler {:keys [xform] :or {xform identity}}]
      (let [logger (xform log-req)]
        (fn [req]
          (log/with-duration [ctx (handler req)]
            (logger ctx req)))))))

#?(:clj
   (defn with-error-handling
     "Catches exceptions and generates an response via [[routes.err/ex->response]]."
     [handler]
     (fn [req]
       (try (handler req)
            (catch Throwable ex
              (let [msg (ex-message ex)
                    data (ex-data ex)]
                (log/error ex msg data)
                (routes.err/ex->response (maps/assoc-defaults data :message msg))))))))
