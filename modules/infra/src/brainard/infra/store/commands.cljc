(ns brainard.infra.store.commands
  (:require
   [brainard.infra.store.core :as store]
   [clojure.string :as string]
   [whet.utils.navigation :as nav]
   [clojure.core.async :as async]
   [defacto.core :as defacto]
   [whet.core :as-alias w]))

(defonce ^:private ->sortable-id
         (let [id (atom 0)]
           (fn []
             (swap! id inc))))

(defmethod defacto/command-handler ::w/with-qp!
  [{::defacto/keys [store] ::w/keys [nav]} [_ query-params] _]
  (let [{:keys [token route-params]} (store/query store [::w/?:route])]
    (nav/navigate! nav token route-params query-params)))

(defmethod defacto/command-handler :nav/navigate!
  [{::w/keys [nav]} [_ {:keys [token query-params route-params]}] _]
  (nav/navigate! nav token route-params query-params))

(defmethod defacto/command-handler :nav/replace!
  [{::w/keys [nav]} [_ {:keys [token query-params route-params]}] _]
  (nav/replace! nav token route-params query-params))

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
    (async/<! (async/timeout 333))
    (emit-cb [:modals/destroyed modal-id])))

(defmethod defacto/command-handler :modals/remove-all!
  [_ _ emit-cb]
  (emit-cb [:modals/all-hidden])
  (async/go
    (async/<! (async/timeout 333))
    (emit-cb [:modals/all-destroyed])))


(defmethod defacto/command-handler :toasts/succeed!
  [{::defacto/keys [store]} [_ {:keys [message]}] _]
  (store/dispatch! store [:toasts/create! :success message]))

(defmethod defacto/command-handler :toasts/fail!
  [{::defacto/keys [store]} [_ err] _]
  (store/dispatch! store [:toasts/create! :error (or (:message err)
                                                     (when (and (seqable? err) (seq err))
                                                       (some->> (map :message err)
                                                                (string/join "\n")
                                                                not-empty))
                                                     "An error occurred")]))

(defmethod defacto/command-handler :toasts/hide!
  [_ [_ toast-id] emit-cb]
  (emit-cb [:toasts/hidden toast-id])
  (async/go
    (async/<! (async/timeout 650))
    (emit-cb [:toasts/destroyed toast-id])))

(defmethod defacto/command-handler :toasts/create!
  [_ [_ level body timeout] emit-cb]
  (let [toast-id (->sortable-id)]
    (emit-cb [:toasts/created toast-id {:state   :init
                                        :level   level
                                        :body    body
                                        :timeout timeout}])))

(defmethod defacto/command-handler :toasts.notes/succeed!
  [_ [_ note] emit-cb]
  (let [toast-id (->sortable-id)]
    (emit-cb [:toasts/created toast-id {:state :init
                                        :level :success
                                        :body  [::new-note (:notes/id note)]}])))
