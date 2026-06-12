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
  (fn [req respond raise]
    (-> req
        (assoc :brainard/input (iroutes/req->input req))
        (handler respond raise))))

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
        (fn [req respond raise]
          (let [before (long (/ (System/nanoTime) 1000000))]
            (handler req
                     (fn [response]
                       (logger {:result   response
                                :duration (- (long (/ (System/nanoTime) 1000000)) before)}
                               req)
                       (respond response))
                     (fn [ex]
                       (logger {:ex       ex
                                :duration (- (long (/ (System/nanoTime) 1000000)) before)}
                               req)
                       (raise ex)))))))))

#?(:clj
   (defn ^:private ex->resp [ex]
     (let [msg (ex-message ex)
           data (ex-data ex)]
       (log/error ex msg data)
       (routes.err/ex->response (maps/assoc-defaults data :message msg)))))

#?(:clj
   (defn with-error-handling
     "Catches exceptions and generates an response via [[routes.err/ex->response]]."
     [handler]
     (fn [req respond _raise]
       (try
         (handler req respond (comp respond ex->resp))
         (catch Throwable ex
           (respond (ex->resp ex)))))))
