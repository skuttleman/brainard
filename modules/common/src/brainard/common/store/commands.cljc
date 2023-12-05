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
  (store.api/request! store
                      http
                      {:route        :routes.api/tags
                       :method       :get
                       :on-success-n [[::store/emit! [:resources/succeeded :api.tags/select!]]]
                       :on-error-n   [[::store/emit! [:resources/failed :api.tags/select! :remote]]]}))

(defmethod defacto/command-handler :api.contexts/select!
  [{::defacto/keys [store] :services/keys [http]} _ _]
  (store.api/request! store
                      http
                      {:route        :routes.api/contexts
                       :method       :get
                       :on-success-n [[::store/emit! [:resources/succeeded :api.contexts/select!]]]
                       :on-error-n   [[::store/emit! [:resources/failed :api.contexts/select! :remote]]]}))

(defmethod defacto/command-handler :api.notes/select!
  [{::defacto/keys [store] :services/keys [http]} [_ resource-id params] _]
  (store.api/request! store
                      http
                      {:route        :routes.api/notes
                       :method       :get
                       :params       {:query-params params}
                       :on-success-n [[::store/emit! [:resources/succeeded [:api.notes/select! resource-id]]]]
                       :on-error-n   [[::store/emit! [:resources/failed [:api.notes/select! resource-id] :remote]]]}))

(defmethod defacto/command-handler :api.notes/find!
  [{::defacto/keys [store] :services/keys [http]} [_ resource-id] _]
  (store.api/request! store
                      http
                      {:route        :routes.api/note
                       :method       :get
                       :params       {:notes/id resource-id}
                       :on-success-n [[::store/emit! [:resources/succeeded [:api.notes/find! resource-id]]]]
                       :on-error-n   [[::store/emit! [:resources/failed [:api.notes/find! resource-id] :remote]]]}))

(defmethod defacto/command-handler :api.notes/create!
  [{::defacto/keys [store] :services/keys [http]} [_ resource-id {:keys [data reset-to]}] _]
  (store.api/request! store
                      http
                      {:route        :routes.api/notes
                       :method       :post
                       :body         data
                       :on-success-n (cond-> [[:toasts/succeed! {:message "note created"}]
                                              [::store/emit! [:resources/note-saved]]]
                                       reset-to
                                       (conj [::store/emit! [:forms/created resource-id reset-to]]
                                             [::store/emit! [:resources/destroyed [:api.notes/create! resource-id]]])

                                       (nil? reset-to)
                                       (conj [::store/emit! [:resources/succeeded [:api.notes/create! resource-id]]]))
                       :on-error-n   [[:toasts/fail!]
                                      [::store/emit! [:resources/failed [:api.notes/create! resource-id] :remote]]]}))

(defmethod defacto/command-handler :api.notes/update!
  [{::defacto/keys [store] :services/keys [http]} [_ resource-id {:keys [note-id data fetch? reset-to]}] _]
  (store.api/request! store
                      http
                      {:route        :routes.api/note
                       :params       {:notes/id note-id}
                       :method       :patch
                       :body         data
                       :on-success-n (cond-> [[:toasts/succeed! {:message "note updated"}]
                                              [::store/emit! [:resources/note-saved]]]
                                       reset-to
                                       (conj [::store/emit! [:forms/created resource-id reset-to]]
                                             [::store/emit! [:resources/destroyed [:api.notes/update! resource-id]]])

                                       (nil? reset-to)
                                       (conj [::store/emit! [:resources/succeeded [:api.notes/update! resource-id]]])

                                       fetch?
                                       (conj [:resources/submit! [:api.notes/find! note-id]]))
                       :on-error-n   [[:toasts/fail!]
                                      [::store/emit! [:resources/failed [:api.notes/update! resource-id] :remote]]]}))

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
