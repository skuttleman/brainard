(ns brainard.common.store.commands
  (:require
    [brainard.common.store.core :as store]
    [brainard.common.stubs.nav :as nav]
    [clojure.core.async :as async]
    [defacto.core :as defacto]))

(defonce ^:private ->sortable-id
  (let [id (atom 0)]
    (fn []
      (swap! id inc))))

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
  [{::defacto/keys [store]} _ _]
  (store/dispatch! store [:toasts/create! :error "An error occurred"]))

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
        body [:span.layout--align-center
              "a"
              [:button.button.is-test.is-ghost
               {:on-click (fn [_]
                            ;; TODO - why does :a not work here?
                            (nav/navigate! nav :routes.ui/note (select-keys note #{:notes/id})))}
               "new note"]
              "was created"]]
    (emit-cb [:toasts/created toast-id {:state :init
                                        :level :success
                                        :body  body}])))
