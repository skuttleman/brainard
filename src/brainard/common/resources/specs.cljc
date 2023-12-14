(ns brainard.common.resources.specs
  (:require
    [brainard.common.utils.routing :as rte]
    [brainard.common.validations.core :as valid]
    [defacto.forms.core :as forms]
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
          :method :get}
         nil))

(defmethod res/->request-spec ::contexts#select
  [_ _]
  (->req {:route  :routes.api/contexts
          :method :get}))

(def ^:private search-validator
  (valid/->validator valid/notes-query))

(defmethod res/->request-spec ::notes#select
  [[_ resource-id] {:keys [changed? data pre-commands]}]
  (if-let [errors (search-validator data)]
    (if changed?
      {:pre-events [[::res/failed [::notes#select resource-id]
                     (with-meta errors {:local? true})]]}
      {:pre-events [[::res/destroyed [::notes#select resource-id]]]})
    (->req {:route  :routes.api/notes
            :method :get
            :params {:query-params data}}
           {:pre-commands pre-commands})))

(defmethod res/->request-spec ::notes#find
  [[_ resource-id] _]
  (->req {:route  :routes.api/note
          :method :get
          :params {:notes/id resource-id}}))

(def ^:private new-note-validator
  (valid/->validator valid/new-note))

(defmethod res/->request-spec ::notes#create
  [[_ resource-id] {:keys [data reset-to]}]
  (if-let [errors (new-note-validator data)]
    {:pre-events [[::res/failed [::notes#create resource-id]
                   (with-meta errors {:local? true})]]}
    (->req {:route        :routes.api/notes
            :method       :post
            :body         data
            :ok-events    (cond-> [[:api.notes/saved]]
                            reset-to
                            (conj [::forms/created resource-id reset-to]
                                  [::res/destroyed [::notes#create resource-id]]))
            :ok-commands  [[:toasts.notes/succeed!]]
            :err-commands [[:toasts/fail!]]})))

(defmethod res/->request-spec ::notes#update
  [[_ resource-id] {:keys [note-id data fetch? reset-to]}]
  (->req {:route        :routes.api/note
          :params       {:notes/id note-id}
          :method       :patch
          :body         data
          :ok-events    (cond-> [[:api.notes/saved]]
                          reset-to
                          (conj [::forms/created resource-id reset-to]))
          :ok-commands  (cond-> [[:toasts/succeed! {:message "note updated"}]]
                          fetch?
                          (conj [::res/submit! [::notes#find note-id]]))
          :err-commands [[:toasts/fail!]]}))

(defmethod res/->request-spec ::notes#buzz
  [_ _]
  (->req {:route  :routes.api/notes?scheduled
          :method :get}))

(def ^:private new-schedule-validator
  (valid/->validator valid/new-schedule))

(defmethod res/->request-spec ::schedules#create
  [[_ resource-id] {:keys [data reset-to]}]
  (if-let [errors (new-schedule-validator data)]
    {:pre-events [[::res/failed [::schedules#create resource-id] (with-meta errors {:local? true})]]}
    (let [ok-events (when reset-to
                      [[::forms/created resource-id reset-to]
                       [::res/destroyed [::schedules#create resource-id]]])]
      (->req {:route        :routes.api/schedules
              :method       :post
              :body         data
              :ok-events    (conj ok-events [:api.schedules/saved (:schedules/note-id data)])
              :ok-commands  [[:toasts/succeed! {:message "schedule created"}]]
              :err-commands [[:toasts/fail!]]}))))

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
