(ns brainard.common.store.commands
  (:require
    [brainard.common.store.api :as store.api]
    [brainard.common.store.core :as store]
    [brainard.common.store.specs :as rspecs]
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
  [{::defacto/keys [store]} [_ form-id params] emit-cb]
  (when-not (defacto/query-responder @store [:forms/form form-id])
    (emit-cb [:forms/created form-id params])))

(defmethod defacto/command-handler :resources/ensure!
  [{::defacto/keys [store]} [_ resource-id params] _]
  (#?(:cljs async/go :default do)
    #?(:cljs (async/<! (async/timeout 1)))
    (when (= [:init] (defacto/query-responder @store [:resources/?:resource resource-id]))
      (store/dispatch! store [:resources/submit! resource-id params]))))

(defmethod defacto/command-handler :resources/submit!
  [{::defacto/keys [store]} [_ resource-id params] emit-cb]
  (let [req (rspecs/->req {::rspecs/spec resource-id
                           :params       params})]
    (emit-cb [:resources/submitted resource-id])
    (store/dispatch! store [::store.api/request! req])))

(defmethod defacto/command-handler :routing/with-qp!
  [{::defacto/keys [store] :services/keys [nav]} [_ query-params] _]
  (let [{:keys [token route-params]} (defacto/query-responder @store [:routing/?:route])]
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
