(ns brainard.common.store.commands
  (:require
    [brainard.common.resources.api :as rapi]
    [brainard.common.store.core :as store]
    [brainard.common.resources.specs :as rspecs]
    [brainard.common.stubs.nav :as nav]
    [clojure.core.async :as async]
    [clojure.string :as string]
    [defacto.core :as defacto])
  #?(:clj
     (:import
       (java.util Date))))

(defonce ^:private *:toast-id
  (atom #?(:cljs (.getTime (js/Date.)) :default (.getTime (Date.)))))

(defmethod defacto/command-handler :forms/ensure!
  [{::defacto/keys [store]} [_ form-id params opts] emit-cb]
  (when-not (store/query store [:forms/?:form form-id])
    (emit-cb [:forms/created form-id params opts])))

(defmethod defacto/command-handler :resources/ensure!
  [{::defacto/keys [store]} [_ resource-id params] _]
  (when (= :init (:status (store/query store [:resources/?:resource resource-id])))
    (store/dispatch! store [:resources/submit! resource-id params])))

(defmethod defacto/command-handler :resources/sync!
  [{::defacto/keys [store]} [_ resource-id next-params] _]
  (let [{:keys [status params]} (store/query store [:resources/?:resource resource-id])]
    (when (or (= :init status) (not= params next-params))
      (store/dispatch! store [:resources/submit! resource-id next-params]))))

(defmethod defacto/command-handler :resources/submit!
  [{::defacto/keys [store]} [_ resource-id params] emit-cb]
  (let [input (rspecs/->req {::rspecs/spec resource-id
                             :params       params})]
    (emit-cb [:resources/submitted resource-id params])
    (store/dispatch! store [::rapi/request! input])))

(defmethod defacto/command-handler :routing/with-qp!
  [{::defacto/keys [store] :services/keys [nav]} [_ query-params] _]
  (let [{:keys [token route-params]} (store/query store [:routing/?:route])]
    (nav/navigate! nav token (assoc route-params :query-params query-params))))

(defmethod defacto/command-handler :toasts/succeed!
  [{::defacto/keys [store]} [_ {:keys [message]}] _]
  (store/dispatch! store [:toasts/create! :success message]))

(defmethod defacto/command-handler :toasts/fail!
  [{::defacto/keys [store]} [_ errors] _]
  (let [msg (if-let [messages (seq (keep :message errors))]
              (string/join ", " messages)
              "An unknown error occurred")]
    (store/dispatch! store [:toasts/create! :error msg])))

(defmethod defacto/command-handler :toasts/hide!
  [_ [_ toast-id] emit-cb]
  (emit-cb [:toasts/hidden toast-id])
  (async/go
    (async/<! (async/timeout 650))
    (emit-cb [:toasts/destroyed toast-id])))

(defmethod defacto/command-handler :toasts/create!
  [_ [_ level body] emit-cb]
  (let [toast-id (swap! *:toast-id inc)]
    (emit-cb [:toasts/created toast-id {:state :init
                                        :level level
                                        :body  body}])))