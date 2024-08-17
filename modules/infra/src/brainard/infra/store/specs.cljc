(ns brainard.infra.store.specs
  (:require
    [clojure.set :as set]
    [defacto.resources.core :as res]))

(defn ^:private with-msgs [m k params spec]
  (if-let [v (seq (concat (get spec k) (get params k) (get-in spec [:params k])))]
    (assoc m k (vec v))
    m))

(defn ^:private ->req
  ([params]
   (->req params nil))
  ([{:keys [route] :as params} spec]
   (let [{:keys [query-params] :as route-params} (:params params)]
     (-> {:params {:request-method (:method params)
                   :route          {:token        route
                                    :route-params (dissoc route-params :query-params)
                                    :query-params query-params}
                   :body           (:body params)}
          :->ok   :data
          :->err  :errors}
         (with-msgs :pre-events params spec)
         (with-msgs :pre-commands params spec)
         (with-msgs :ok-events params spec)
         (with-msgs :ok-commands params spec)
         (with-msgs :err-events params spec)
         (with-msgs :err-commands params spec)))))

(defmethod res/->request-spec ::tags#select
  [_ spec]
  (->req {:route  :routes.api/tags
          :method :get}
         spec))

(defmethod res/->request-spec ::contexts#select
  [_ spec]
  (->req {:route  :routes.api/contexts
          :method :get}
         spec))

(defmethod res/->request-spec ::notes#select
  [_ {:keys [payload] :as spec}]
  (->req {:route  :routes.api/notes
          :method :get
          :params {:query-params payload}}
         spec))

(defmethod res/->request-spec ::notes#find
  [[_ resource-id] spec]
  (->req {:route  :routes.api/note
          :method :get
          :params {:notes/id resource-id}}
         spec))

(defmethod res/->request-spec ::notes#create
  [_ {:keys [payload] :as spec}]
  (->req {:route        :routes.api/notes
          :method       :post
          :body         payload
          :ok-events    [[:api.notes/saved]]
          :ok-commands  [[:toasts.notes/succeed!]]
          :err-commands [[:toasts/fail!]]}
         spec))

(defn ^:private diff-tags [old curr]
  (let [removals (set/difference old curr)]
    {:notes/tags!remove (or removals #{})
     :notes/tags        (or curr #{})}))

(defmethod res/->request-spec ::notes#modify
  [_ {:keys [note prev-tags] :as spec}]
  (->req {:route        :routes.api/note
          :params       (select-keys note #{:notes/id})
          :method       :patch
          :body         (-> note
                            (select-keys #{:notes/context
                                           :notes/pinned?
                                           :notes/body})
                            (merge (diff-tags prev-tags (:notes/tags note))))}
         spec))

(defmethod res/->request-spec ::notes#destroy
  [[_ note-id] spec]
  (->req {:route        :routes.api/note
          :params       {:notes/id note-id}
          :method       :delete}
         spec))

(defmethod res/->request-spec ::notes#buzz
  [_ spec]
  (->req {:route  :routes.api/notes?scheduled
          :method :get}
         spec))

(defmethod res/->request-spec ::note#history
  [[_ note-id] spec]
  (->req {:route  :routes.api/note?history
          :method :get
          :params {:notes/id note-id}}
         spec))

(defmethod res/->request-spec ::schedules#create
  [_ {:keys [payload] :as spec}]
  (->req {:route        :routes.api/schedules
          :method       :post
          :body         payload
          :ok-events    [[:api.schedules/saved (:schedules/note-id payload)]]
          :ok-commands  [[:toasts/succeed! {:message "schedule created"}]]
          :err-commands [[:toasts/fail!]]}
         spec))

(defmethod res/->request-spec ::schedules#destroy
  [[_ resource-id :as resource-key] spec]
  (->req {:route        :routes.api/schedule
          :method       :delete
          :params       {:schedules/id resource-id}
          :ok-events    [[:api.schedules/deleted resource-id (:notes/id spec)]
                         [::res/destroyed resource-key]]
          :ok-commands  [[:toasts/succeed! {:message "schedule deleted"}]]
          :err-events   [[::res/destroyed resource-key]]
          :err-commands [[:toasts/fail!]]}
         spec))

(defmethod res/->request-spec ::workspace#select
  [_ spec]
  (->req {:route        :routes.api/workspace-nodes
          :method       :get
          :err-commands [[:toasts/fail!]]}
         spec))

(defmethod res/->request-spec ::workspace#create
  [_ {:keys [payload] :as spec}]
  (->req {:route        :routes.api/workspace-nodes
          :method       :post
          :body         payload
          :err-commands [[:toasts/fail!]]}
         spec))

(defmethod res/->request-spec ::workspace#modify
  [[_ resource-id] {:keys [payload] :as spec}]
  (->req {:route        :routes.api/workspace-node
          :method       :patch
          :params       {:workspace-nodes/id resource-id}
          :body         payload
          :err-commands [[:toasts/fail!]]}
         spec))

(defmethod res/->request-spec ::workspace#destroy
  [[_ resource-id] spec]
  (->req {:route        :routes.api/workspace-node
          :method       :delete
          :params       {:workspace-nodes/id resource-id}
          :err-commands [[:toasts/fail!]]}
         spec))

(defmethod res/->request-spec ::apps#create
  [_ {:keys [payload] :as spec}]
  (->req {:route        :routes.api/applications
          :method       :post
          :body         payload
          :err-commands [[:toasts/fail!]]
          :ok-commands  [[:toasts.applications/succeed!]]}
         spec))

(defmethod res/->request-spec ::apps#select
  [_ spec]
  (->req {:route  :routes.api/applications
          :method :get}
         spec))

(defmethod res/->request-spec ::apps#find
  [[_ resource-id] spec]
  (->req {:route  :routes.api/application
          :method :get
          :params {:applications/id resource-id}}
         spec))
