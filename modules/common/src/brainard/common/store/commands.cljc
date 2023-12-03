(ns brainard.common.store.commands
  (:require
    #?(:cljs [brainard.ui.services.navigation.core :as nav])
    [brainard.common.store.core :as store]
    [brainard.common.utils.colls :as colls]
    [brainard.common.store.api :as store.api]
    [clojure.core.async :as async]
    [clojure.string :as string]
    [yast.core :as yast])
  #?(:clj
     (:import
       (java.util Date))))

(defmethod yast/command-handler :default
  [_ _ action emit]
  (emit action))

(defmethod yast/command-handler :resources/submit!
  [store db [_ resource-id params] emit]
  (let [resource (colls/wrap-vector resource-id)
        mixins (meta resource)
        {:keys [handler route-params]} (yast/query db [:routing/?route])]
    (emit [:resources/submitted resource-id])
    (store/dispatch! store (conj resource params))
    (when (:with-qp-sync? mixins)
      #?(:cljs
         (nav/navigate! handler (assoc route-params :query-params params))))))

(defmethod yast/command-handler :api.tags/select!
  [store _ _ _]
  (store.api/request! store
                      {:route        :routes.api/tags
                       :method       :get
                       :on-success-n [[:resources/succeeded :api.tags/select!]]
                       :on-error-n   [[:resources/failed :api.tags/select! :remote]]}))

(defmethod yast/command-handler :api.contexts/select!
  [store _ _ _]
  (store.api/request! store
                      {:route        :routes.api/contexts
                       :method       :get
                       :on-success-n [[:resources/succeeded :api.contexts/select!]]
                       :on-error-n   [[:resources/failed :api.contexts/select! :remote]]}))

(defmethod yast/command-handler :api.notes/select!
  [store _ [_ resource-id params] _]
  (store.api/request! store
                      {:route        :routes.api/notes
                       :method       :get
                       :params       {:query-params params}
                       :on-success-n [[:resources/succeeded [:api.notes/select! resource-id]]]
                       :on-error-n   [[:resources/failed [:api.notes/select! resource-id] :remote]]}))

(defmethod yast/command-handler :api.notes/find!
  [store _ [_ resource-id] _]
  (store.api/request! store
                      {:route        :routes.api/note
                       :method       :get
                       :params       {:notes/id resource-id}
                       :on-success-n [[:resources/succeeded [:api.notes/find! resource-id]]]
                       :on-error-n   [[:resources/failed [:api.notes/find! resource-id] :remote]]}))

(defmethod yast/command-handler :api.notes/create!
  [store _ [_ resource-id {:keys [data reset-to]}] _]
  (store.api/request! store
                      {:route        :routes.api/notes
                       :method       :post
                       :body         data
                       :on-success-n (cond-> [[:toasts/succeed! {:message "note created"}]
                                              [:resources/note-saved]]
                                       reset-to
                                       (conj [:forms/created resource-id reset-to]
                                             [:resources/destroyed [:api.notes/create! resource-id]])

                                       (nil? reset-to)
                                       (conj [:resources/succeeded [:api.notes/create! resource-id]]))
                       :on-error-n   [[:toasts/fail!]
                                      [:resources/failed [:api.notes/create! resource-id] :remote]]}))

(defmethod yast/command-handler :api.notes/update!
  [store _ [_ resource-id {:keys [note-id data fetch? reset-to]}] _]
  (store.api/request! store
                      {:route        :routes.api/note
                       :params {:notes/id note-id}
                       :method       :patch
                       :body         data
                       :on-success-n (cond-> [[:toasts/succeed! {:message "note updated"}]
                                              [:resources/note-saved]]
                                       reset-to
                                       (conj [:forms/created resource-id reset-to]
                                             [:resources/destroyed [:api.notes/update! resource-id]])

                                       (nil? reset-to)
                                       (conj [:resources/succeeded [:api.notes/update! resource-id]])

                                       fetch?
                                       (conj [:resources/submit! [:api.notes/find! note-id]]))
                       :on-error-n   [[:toasts/fail!]
                                      [:resources/failed [:api.notes/update! resource-id] :remote]]}))

(defmethod yast/command-handler :toasts/succeed!
  [store _ [_ {:keys [message]}] _]
  (store/dispatch! store [:toasts/create! :success message]))

(defmethod yast/command-handler :toasts/fail!
  [store _ [_ errors] _]
  (let [msg (if-let [messages (seq (keep :message errors))]
              (string/join ", " messages)
              "An unknown error occurred")]
    (store/dispatch! store [:toasts/create! :error msg])))

(defmethod yast/command-handler :toasts/hide!
  [store _ [_ toast-id] emit]
  (emit [:toasts/hidden toast-id])
  (async/go
    (async/<! (async/timeout 650))
    (store/dispatch! store [:toasts/destroyed toast-id])))

(defmethod yast/command-handler :toasts/create!
  [_ _ [_ level body] emit]
  (let [toast-id #?(:cljs (.getTime (js/Date.)) :default (.getTime (Date.)))]
    (emit [:toasts/created toast-id {:state :init
                                    :level level
                                    :body  body}])))
