(ns brainard.common.store.commands
  (:require
    [brainard.common.resources.api :as rapi]
    [brainard.common.store.core :as store]
    [brainard.common.resources.specs :as rspecs]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.nav :as nav]
    [brainard.common.utils.routing :as rte]
    [clojure.core.async :as async]
    [clojure.string :as string]
    [defacto.core :as defacto]))

(defonce ^:private ->sortable-id
  (let [id (atom 0)]
    (fn []
      (swap! id inc))))

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

(defmethod defacto/command-handler :resources/after!
  [{::defacto/keys [store]} [_ ms command] _]
  #?(:cljs
     (async/go
       (async/<! (async/timeout ms))
       (store/dispatch! store command))))

(defmethod defacto/command-handler :resources/submit!
  [{::defacto/keys [store]} [_ resource-id params] emit-cb]
  (emit-cb [:resources/submitted resource-id params])
  (store/dispatch! store [:resources/quietly! resource-id params]))

(defmethod defacto/command-handler :resources/quietly!
  [{::defacto/keys [store]} [_ resource-id params] _]
  (let [input (rspecs/->req {::rspecs/type resource-id
                             :params       params})]
    (store/dispatch! store [::rapi/request! input])))

(defmethod defacto/command-handler :routing/with-qp!
  [{::defacto/keys [store] :services/keys [nav]} [_ query-params] _]
  (let [{:keys [token route-params]} (store/query store [:routing/?:route])]
    (nav/navigate! nav token (assoc route-params :query-params query-params))))


(defmethod defacto/command-handler :modals/create!
  [_ [_ body] emit-cb]
  (let [modal-id (->sortable-id)]
    (emit-cb [:modals/created modal-id {:state :init
                                        :body  body}])
    (async/go
      (async/<! (async/timeout 10))
      (emit-cb [:modals/displayed modal-id]))))

(defmethod defacto/command-handler :modals/remove!
  [_ [_ modal-id] emit-cb]
  (emit-cb [:modals/hidden modal-id])
  (async/go
    (async/<! (async/timeout 500))
    (emit-cb [:modals/destroyed modal-id])))

(defmethod defacto/command-handler :modals/remove-all!
  [_ _ emit-cb]
  (emit-cb [:modals/all-hidden])
  (async/go
    (async/<! (async/timeout 500))
    (emit-cb [:modals/all-destroyed])))


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
  (let [toast-id (->sortable-id)]
    (emit-cb [:toasts/created toast-id {:state :init
                                        :level level
                                        :body  body}])))

(defmethod defacto/command-handler :toasts.notes/succeed!
  [{:services/keys [nav]} [_ note] emit-cb]
  (let [toast-id (->sortable-id)
        body [:span
              "a "
              [:a.link.is-link
               {:href     "#"
                :on-click (fn [e]
                            (dom/prevent-default! e)
                            (dom/stop-propagation! e)
                            (nav/navigate! nav :routes.ui/note (select-keys note #{:notes/id})))}
               "new note"]
              " was created"]]
    (emit-cb [:toasts/created toast-id {:state :init
                                        :level :success
                                        :body  body}])))
