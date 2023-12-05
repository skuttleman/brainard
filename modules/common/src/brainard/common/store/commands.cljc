(ns brainard.common.store.commands
  (:require
    #?(:cljs [brainard.ui.services.navigation.core :as nav])
    [brainard.common.store.core :as store]
    [brainard.common.utils.colls :as colls]
    [brainard.common.store.api :as store.api]
    [clojure.core.async :as async]
    [clojure.string :as string]
    [defacto.core :as defacto])
  #?(:clj
     (:import
       (java.util Date))))

(defmethod defacto/command-handler :forms/ensure!
  [{::defacto/keys [store]} [_ form-id & more] emit-cb]
  (when-not (get-in @store [:forms/forms form-id])
    (emit-cb (into [:forms/created form-id] more))))

(defmethod defacto/command-handler :resources/ensure!
  [{::defacto/keys [store]} [_ resource-id & more] _]
  (when-not (get-in @store [:resources/resources resource-id])
    (store/dispatch! store (into [:resources/submit! resource-id] more))))

(defmethod defacto/command-handler :resources/submit!
  [{::defacto/keys [store]} [_ resource-id params] emit-cb]
  (let [resource (colls/wrap-vector resource-id)
        mixins (meta resource)
        {:keys [handler route-params]} (defacto/query @store [:routing/?route])]
    (emit-cb [:resources/submitted resource-id])
    (store/dispatch! store (conj resource params))
    (when (:with-qp-sync? mixins)
      #?(:cljs
         (nav/navigate! handler (assoc route-params :query-params params))))))

(defmethod defacto/command-handler :api.tags/select!
  [{::defacto/keys [store] :services/keys [http]} _ _]
  (store.api/request! store http {::store.api/spec :api.tags/select!}))

(defmethod defacto/command-handler :api.contexts/select!
  [{::defacto/keys [store] :services/keys [http]} _ _]
  (store.api/request! store http {::store.api/spec :api.contexts/select!}))

(defmethod defacto/command-handler :api.notes/select!
  [{::defacto/keys [store] :services/keys [http]} [_ resource-id params] _]
  (store.api/request! store http {::store.api/spec :api.notes/select!
                                  :resource/id     resource-id
                                  :params          {:query-params params}}))

(defmethod defacto/command-handler :api.notes/find!
  [{::defacto/keys [store] :services/keys [http]} [_ resource-id] _]
  (store.api/request! store
                      http
                      {::store.api/spec :api.notes/find!
                       :resource/id     resource-id
                       :params          {:notes/id resource-id}}))

(defmethod defacto/command-handler :api.notes/create!
  [{::defacto/keys [store] :services/keys [http]} [_ resource-id {:keys [data reset-to]}] _]
  (store.api/request! store
                      http
                      {::store.api/spec :api.notes/create!
                       :resource/id     resource-id
                       :body            data
                       :on-success-n    (if reset-to
                                          [[::store/emit! [:forms/created resource-id reset-to]]
                                           [::store/emit! [:resources/destroyed [:api.notes/create! resource-id]]]]
                                          [[::store/emit! [:resources/succeeded [:api.notes/create! resource-id]]]])}))

(defmethod defacto/command-handler :api.notes/update!
  [{::defacto/keys [store] :services/keys [http]} [_ resource-id {:keys [note-id data fetch? reset-to]}] _]
  (store.api/request! store
                      http
                      {::store.api/spec :api.notes/update!
                       :params       {:notes/id note-id}
                       :body         data
                       :on-success-n (cond-> []
                                       reset-to
                                       (conj [::store/emit! [:forms/created resource-id reset-to]]
                                             [::store/emit! [:resources/destroyed [:api.notes/update! resource-id]]])

                                       (nil? reset-to)
                                       (conj [::store/emit! [:resources/succeeded [:api.notes/update! resource-id]]])

                                       fetch?
                                       (conj [:resources/submit! [:api.notes/find! note-id]]))}))

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
  (let [toast-id #?(:cljs (.getTime (js/Date.)) :default (.getTime (Date.)))]
    (emit-cb [:toasts/created toast-id {:state :init
                                        :level level
                                        :body  body}])))
