(ns brainard.infra.store.specs
  (:require
    [brainard.api.validations :as valid]
    [brainard.applications.api.specs :as sapps]
    [brainard.notes.api.specs :as snotes]
    [brainard.schedules.api.specs :as ssched]
    [brainard.workspace.api.specs :as sws]
    [clojure.set :as set]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
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

(let [vld (valid/->validator snotes/query)]
  (defmethod forms+/validate ::notes#select [_ data] (vld data)))
(defmethod forms+/re-init ::notes#select [_ form _] (forms/data form))
(defmethod res/->request-spec ::notes#select
  [_ {::forms/keys [data] :as spec}]
  (->req {:route  :routes.api/notes
          :method :get
          :params {:query-params data}}
         spec))

(defmethod res/->request-spec ::notes#find
  [[_ resource-id] spec]
  (->req {:route  :routes.api/note
          :method :get
          :params {:notes/id resource-id}}
         spec))

(let [vld (valid/->validator snotes/create)]
  (defmethod forms+/validate ::notes#create [_ data] (vld data)))
(defmethod res/->request-spec ::notes#create
  [_ {::forms/keys [data] :as spec}]
  (->req {:route        :routes.api/notes
          :method       :post
          :body         (select-keys data #{:notes/context
                                            :notes/pinned?
                                            :notes/body
                                            :notes/tags})
          :ok-events    [[:api.notes/saved]]
          :ok-commands  [[:toasts.notes/succeed!]]
          :err-commands [[:toasts/fail!]]}
         spec))

(defn ^:private diff-tags [old curr]
  (let [removals (set/difference old curr)]
    {:notes/tags!remove removals
     :notes/tags        curr}))

(defmethod forms+/re-init ::notes#update [_ _ result] result)
(defmethod res/->request-spec ::notes#update
  [_ {::forms/keys [data] :keys [prev-tags] :as spec}]
  (->req {:route        :routes.api/note
          :params       (select-keys data #{:notes/id})
          :method       :patch
          :body         (-> data
                            (select-keys #{:notes/context
                                           :notes/pinned?
                                           :notes/body})
                            (merge (diff-tags prev-tags (:notes/tags data))))
          :ok-events    [[:api.notes/saved]]
          :ok-commands  [[:toasts/succeed! {:message "note updated"}]]
          :err-commands [[:toasts/fail!]]}
         spec))

(defmethod res/->request-spec ::notes#reinstate
  [resource-key {:keys [note prev-tags] :as spec}]
  (let [note-id (:notes/id note)]
    (->req {:route        :routes.api/note
            :params       {:notes/id note-id}
            :method       :patch
            :body         (-> note
                              (select-keys #{:notes/context
                                             :notes/pinned?
                                             :notes/body})
                              (merge (diff-tags prev-tags (:notes/tags note))))
            :ok-events    [[::res/destroyed resource-key]]
            :ok-commands  [[:toasts/succeed! {:message "previous version of note was reinstated"}]
                           [::res/submit! [::notes#find note-id]]
                           [::res/submit! [::note#history note-id]]]
            :err-commands [[:toasts/fail!]]}
           spec)))

(defmethod forms+/re-init ::notes#pin [_ _ result] (select-keys result #{:notes/id :notes/pinned?}))
(defmethod res/->request-spec ::notes#pin
  [_ {::forms/keys [data] :as spec}]
  (->req {:route        :routes.api/note
          :params       (select-keys data #{:notes/id})
          :method       :patch
          :body         (select-keys data #{:notes/pinned?})
          :ok-events    [[:api.notes/saved]]
          :err-commands [[:toasts/fail!]]}
         spec))

(defmethod res/->request-spec ::notes#destroy
  [[_ note-id] spec]
  (->req {:route        :routes.api/note
          :params       {:notes/id note-id}
          :method       :delete
          :ok-commands  [[:toasts/succeed! {:message "note deleted"}]]
          :err-commands [[:toasts/fail!]]}
         spec))

(defmethod res/->request-spec ::notes#pinned
  [_ spec]
  (->req {:route  :routes.api/notes
          :method :get
          :params {:query-params {:pinned true}}}
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

(let [vld (valid/->validator ssched/create)]
  (defmethod forms+/validate ::schedules#create [_ data] (vld data)))
(defmethod res/->request-spec ::schedules#create
  [_ {::forms/keys [data] :as spec}]
  (->req {:route        :routes.api/schedules
          :method       :post
          :body         data
          :ok-events    [[:api.schedules/saved (:schedules/note-id data)]]
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

(let [vld (valid/->validator sws/create)]
  (defmethod forms+/validate ::workspace#create [_ data] (vld data)))
(defmethod res/->request-spec ::workspace#create
  [_ {::forms/keys [data] :as spec}]
  (->req {:route        :routes.api/workspace-nodes
          :method       :post
          :body         data
          :err-commands [[:toasts/fail!]]}
         spec))

(let [vld (valid/->validator sws/modify)]
  (defmethod forms+/validate ::workspace#modify [_ data] (vld data)))
(defmethod res/->request-spec ::workspace#modify
  [[_ resource-id] {::forms/keys [data] :as spec}]
  (->req {:route        :routes.api/workspace-node
          :method       :patch
          :params       {:workspace-nodes/id resource-id}
          :body         data
          :err-commands [[:toasts/fail!]]}
         spec))

(defmethod res/->request-spec ::workspace#move
  [[_ resource-id] {:keys [body] :as spec}]
  (->req {:route        :routes.api/workspace-node
          :method       :patch
          :params       {:workspace-nodes/id resource-id}
          :body         body
          :err-commands [[:toasts/fail!]]}
         spec))

(defmethod res/->request-spec ::workspace#destroy
  [[_ resource-id] spec]
  (->req {:route        :routes.api/workspace-node
          :method       :delete
          :params       {:workspace-nodes/id resource-id}
          :err-commands [[:toasts/fail!]]}
         spec))

(let [vld (valid/->validator sapps/create)]
  (defmethod forms+/validate ::app#new [_ data] (vld data)))
(defmethod res/->request-spec ::app#new
  [_ {::forms/keys [data] :as spec}]
  (->req {:route        :routes.api/applications
          :method       :post
          :body         data
          :err-commands [[:toasts/fail!]]
          :ok-commands  [[:toasts.applications/succeed!]]}
         spec))
