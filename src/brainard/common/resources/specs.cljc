(ns brainard.common.resources.specs
  (:require
    [brainard.common.utils.colls :as colls]
    [brainard.common.utils.routing :as rte]
    [brainard.common.validations.core :as valid]))

(defmulti ^{:arglists '([spec])} resource-spec
          (comp first colls/wrap-vector ::type))

(defn ^:private with-msgs [m k params spec]
  (if-let [v (seq (concat (get spec k) (get params k)))]
    (assoc m k (vec v))
    m))

(defn ^:private ->req [params spec]
  (let [url (some-> (:route params) (rte/path-for (:params params)))]
    (-> {:req {:request-method (:method params)
               :url            url
               :body           (some-> (:body params) pr-str)
               :headers        {"content-type" "application/edn"}}}
        (with-msgs :pre-events params spec)
        (with-msgs :pre-commands params spec)
        (with-msgs :ok-events params spec)
        (with-msgs :ok-commands params spec)
        (with-msgs :err-events params spec)
        (with-msgs :err-commands params spec))))

(defmethod resource-spec ::tags#select
  [spec]
  (->req {:route      :routes.api/tags
          :method     :get
          :ok-events  [[:resources/succeeded ::tags#select]]
          :err-events [[:resources/failed ::tags#select :remote]]}
         spec))

(defmethod resource-spec ::contexts#select
  [spec]
  (->req {:route      :routes.api/contexts
          :method     :get
          :ok-events  [[:resources/succeeded ::contexts#select]]
          :err-events [[:resources/failed ::contexts#select :remote]]}
         spec))

(def ^:private search-validator
  (valid/->validator valid/notes-query))

(defmethod resource-spec ::notes#select
  [{[_ resource-id] ::type {:keys [changed? data]} :params :as spec}]
  (if-let [errors (search-validator data)]
    (if changed?
      {:pre-events [[:resources/failed [::notes#select resource-id] :local errors]]}
      {:pre-events [[:resources/initialized [::notes#select resource-id]]]})
    (->req {:route      :routes.api/notes
            :method     :get
            :params     {:query-params data}
            :ok-events  [[:resources/succeeded [::notes#select resource-id]]]
            :err-events [[:resources/failed [::notes#select resource-id] :remote]]}
           spec)))

(defmethod resource-spec ::notes#find
  [{[_ resource-id] ::type :as spec}]
  (->req {:route      :routes.api/note
          :method     :get
          :params     {:notes/id resource-id}
          :ok-events  [[:resources/succeeded [::notes#find resource-id]]]
          :err-events [[:resources/failed [::notes#find resource-id] :remote]]}
         spec))

(def ^:private new-note-validator
  (valid/->validator valid/new-note))

(defmethod resource-spec ::notes#create
  [{[_ resource-id] ::type {:keys [data reset-to]} :params :as spec}]
  (if-let [errors (new-note-validator data)]
    {:pre-events [[:resources/failed [::notes#create resource-id] :local errors]]}
    (let [ok-events (if reset-to
                      [[:forms/created resource-id reset-to]
                       [:resources/destroyed [::notes#create resource-id]]]
                      [[:resources/succeeded [::notes#create resource-id]]])]
      (->req {:route        :routes.api/notes
              :method       :post
              :body         data
              :ok-events    (conj ok-events [:api.notes/saved])
              :ok-commands  [[:toasts.notes/succeed!]]
              :err-events   [[:resources/failed [::notes#create resource-id] :remote]]
              :err-commands [[:toasts/fail!]]}
             spec))))

(defmethod resource-spec ::notes#update
  [{[_ resource-id] ::type {:keys [note-id data fetch? reset-to]} :params :as spec}]
  (let [ok-events (if reset-to
                    [[:forms/created resource-id reset-to]
                     [:resources/destroyed [::notes#update resource-id]]]
                    [[:resources/succeeded [::notes#update resource-id]]])
        ok-commands (when fetch?
                      [[:resources/submit! [::notes#find note-id]]])]
    (->req {:route        :routes.api/note
            :params       {:notes/id note-id}
            :method       :patch
            :body         data
            :ok-events    (conj ok-events [:api.notes/saved])
            :ok-commands  (conj ok-commands [:toasts/succeed! {:message "note updated"}])
            :err-events   [[:resources/failed [::notes#update resource-id] :remote]]
            :err-commands [[:toasts/fail!]]}
           spec)))

(def ^:private new-schedule-validator
  (valid/->validator valid/new-schedule))

(defmethod resource-spec ::schedules#create
  [{[_ resource-id] ::type {:keys [data reset-to]} :params :as spec}]
  (if-let [errors (new-schedule-validator data)]
    {:pre-events [[:resources/failed [::schedules#create resource-id] :local errors]]}
    (let [ok-events (if reset-to
                      [[:forms/created resource-id reset-to]
                       [:resources/destroyed [::schedules#create resource-id]]]
                      [[:resources/succeeded [::schedules#create resource-id]]])]
      (->req {:route        :routes.api/schedules
              :method       :post
              :body         data
              :ok-events    (conj ok-events [:api.schedules/saved (:schedules/note-id data)])
              :ok-commands  [[:toasts/succeed! {:message "schedule created"}]]
              :err-events   [[:resources/failed [::schedules#create resource-id] :remote]]
              :err-commands [[:toasts/fail!]]}
             spec))))

(defmethod resource-spec ::schedules#destroy
  [{[_ resource-id] ::type :keys [params] :as spec}]
  (->req {:route        :routes.api/schedule
          :method       :delete
          :params       {:schedules/id resource-id}
          :ok-events    [[:resources/destroyed [::schedules#destroy resource-id]]
                         [:api.schedules/deleted resource-id (:notes/id params)]]
          :ok-commands  [[:toasts/succeed! {:message "schedule deleted"}]]
          :err-events   [[:resources/destroyed [::schedules#destroy resource-id]]]
          :err-commands [[:toasts/fail!]]}
         spec))

(defmethod resource-spec ::notes#buzz
  [spec]
  (->req {:route      :routes.api/notes?scheduled
          :method     :get
          :ok-events  [[:resources/succeeded ::notes#buzz]]
          :err-events [[:resources/warned ::notes#buzz]]}
         spec))
