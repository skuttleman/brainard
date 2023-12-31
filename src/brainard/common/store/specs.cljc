(ns brainard.common.store.specs
  (:require
    [brainard.common.utils.routing :as rte]
    [brainard.common.store.validations :as valid]
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
  ([params input]
   (let [url (some-> (:route params) (rte/path-for (:params params)))]
     (-> {:params {:request-method (:method params)
                   :url            url
                   :body           (some-> (:body params) pr-str)
                   :headers        {"content-type" "application/edn"}}}
         (with-msgs :pre-events params input)
         (with-msgs :pre-commands params input)
         (with-msgs :ok-events params input)
         (with-msgs :ok-commands params input)
         (with-msgs :err-events params input)
         (with-msgs :err-commands params input)))))

(defmethod res/->request-spec ::tags#select
  [_ _]
  (->req {:route  :routes.api/tags
          :method :get}))

(defmethod res/->request-spec ::contexts#select
  [_ _]
  (->req {:route  :routes.api/contexts
          :method :get}))

(let [vld (valid/->validator valid/notes-query)]
  (defmethod forms+/validate ::notes#select [_ data] (vld data)))
(defmethod forms+/re-init ::notes#select [_ form _] (forms/data form))
(defmethod res/->request-spec ::notes#select
  [_ {::forms/keys [data] :keys [pre-commands]}]
  (->req {:route  :routes.api/notes
          :method :get
          :params {:query-params data}}
         {:pre-commands pre-commands}))

(defmethod res/->request-spec ::notes#find
  [[_ resource-id] _]
  (->req {:route  :routes.api/note
          :method :get
          :params {:notes/id resource-id}}))

(let [vld (valid/->validator valid/new-note)]
  (defmethod forms+/validate ::notes#create [_ data] (vld data)))
(defmethod res/->request-spec ::notes#create
  [_ {::forms/keys [data] :keys [pre-events ok-events]}]
  (->req {:route        :routes.api/notes
          :method       :post
          :body         data
          :pre-events   pre-events
          :ok-events    (conj ok-events [:api.notes/saved])
          :ok-commands  [[:toasts.notes/succeed!]]
          :err-commands [[:toasts/fail!]]}))

(defn ^:private diff-tags [old new]
  (let [removals (set/difference old new)]
    {:notes/tags!remove removals
     :notes/tags        new}))

(defmethod forms+/re-init ::notes#update [_ _ result] (select-keys result #{:notes/tags}))
(defmethod res/->request-spec ::notes#update
  [_ {::forms/keys [data] :keys [note fetch?]}]
  (->req {:route        :routes.api/note
          :params       (select-keys note #{:notes/id})
          :method       :patch
          :body         (diff-tags (:notes/tags note) (:notes/tags data))
          :ok-events    [[:api.notes/saved]]
          :ok-commands  (cond-> [[:toasts/succeed! {:message "note updated"}]]
                          fetch?
                          (conj [::res/submit! [::notes#find (:notes/id note)]]))
          :err-commands [[:toasts/fail!]]}))

(defmethod res/->request-spec ::notes#buzz
  [_ _]
  (->req {:route  :routes.api/notes?scheduled
          :method :get}))

(let [vld (valid/->validator valid/new-schedule)]
  (defmethod forms+/validate ::schedules#create [_ data] (vld data)))
(defmethod res/->request-spec ::schedules#create
  [_ {::forms/keys [data]}]
  (->req {:route        :routes.api/schedules
          :method       :post
          :body         data
          :ok-events    [[:api.schedules/saved (:schedules/note-id data)]]
          :ok-commands  [[:toasts/succeed! {:message "schedule created"}]]
          :err-commands [[:toasts/fail!]]}))

(defmethod res/->request-spec ::schedules#destroy
  [[_ resource-id] params]
  (->req {:route        :routes.api/schedule
          :method       :delete
          :params       {:schedules/id resource-id}
          :ok-events    [[:api.schedules/deleted resource-id (:notes/id params)]
                         [::res/destroyed [::schedules#destroy resource-id]]]
          :ok-commands  [[:toasts/succeed! {:message "schedule deleted"}]]
          :err-events   [[::res/destroyed [::schedules#destroy resource-id]]]
          :err-commands [[:toasts/fail!]]}))
