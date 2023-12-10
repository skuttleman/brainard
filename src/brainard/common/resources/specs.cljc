(ns brainard.common.resources.specs
  (:require
    [brainard.common.utils.colls :as colls]
    [brainard.common.utils.routing :as rte]
    [brainard.common.validations.core :as valid]))

(defmulti ^:private resource-spec (comp first colls/wrap-vector ::spec))

(defn ->req [spec]
  (let [params (resource-spec spec)
        req-params (select-keys params #{:pre-events
                                         :pre-commands
                                         :ok-events
                                         :ok-commands
                                         :err-events
                                         :err-commands})]
    (if-let [route (:route params)]
      (let [url (rte/path-for route (:params params))]
        (merge {:req {:request-method (:method params)
                      :url            url
                      :body           (some-> (:body params) pr-str)
                      :headers        {"content-type" "application/edn"}}}
               req-params))
      req-params)))

(defmethod resource-spec ::tags#select
  [_]
  {:route      :routes.api/tags
   :method     :get
   :ok-events  [[:resources/succeeded ::tags#select]]
   :err-events [[:resources/failed ::tags#select :remote]]})

(defmethod resource-spec ::contexts#select
  [_]
  {:route      :routes.api/contexts
   :method     :get
   :ok-events  [[:resources/succeeded ::contexts#select]]
   :err-events [[:resources/failed ::contexts#select :remote]]})

(def ^:private search-validator
  (valid/->validator valid/notes-query))

(defmethod resource-spec ::notes#select
  [{[_ resource-id] ::spec {:keys [changed? data pre-commands]} :params}]
  (if-let [errors (search-validator data)]
    (if changed?
      {:pre-events [[:resources/failed [::notes#select resource-id] :local errors]]}
      {:pre-events [[:resources/initialized [::notes#select resource-id]]]})
    {:route        :routes.api/notes
     :method       :get
     :params       {:query-params data}
     :pre-commands pre-commands
     :ok-events    [[:resources/succeeded [::notes#select resource-id]]]
     :err-events   [[:resources/failed [::notes#select resource-id] :remote]]}))

(defmethod resource-spec ::notes#find
  [{[_ resource-id] ::spec}]
  {:route      :routes.api/note
   :method     :get
   :params     {:notes/id resource-id}
   :ok-events  [[:resources/succeeded [::notes#find resource-id]]]
   :err-events [[:resources/failed [::notes#find resource-id] :remote]]})

(def ^:private new-note-validator
  (valid/->validator valid/new-note))

(defmethod resource-spec ::notes#create
  [{[_ resource-id] ::spec {:keys [data pre-events reset-to]} :params}]
  (if-let [errors (new-note-validator data)]
    {:pre-events [[:resources/failed [::notes#create resource-id] :local errors]]}
    (let [ok-events (if reset-to
                      [[:forms/created resource-id reset-to]
                       [:resources/destroyed [::notes#create resource-id]]]
                      [[:resources/succeeded [::notes#create resource-id]]])]
      {:route        :routes.api/notes
       :method       :post
       :body         data
       :pre-events   pre-events
       :ok-events    (conj ok-events [:api.notes/saved])
       :ok-commands  [[:toasts/succeed! {:message "note created"}]]
       :err-events   [[:resources/failed [::notes#create resource-id] :remote]]
       :err-commands [[:toasts/fail!]]})))

(defmethod resource-spec ::notes#update
  [{[_ resource-id] ::spec {:keys [note-id data fetch? reset-to]} :params}]
  (let [ok-events (if reset-to
                    [[:forms/created resource-id reset-to]
                     [:resources/destroyed [::notes#update resource-id]]]
                    [[:resources/succeeded [::notes#update resource-id]]])
        ok-commands (when fetch?
                      [[:resources/submit! [::notes#find note-id]]])]
    {:route        :routes.api/note
     :params       {:notes/id note-id}
     :method       :patch
     :body         data
     :ok-events    (conj ok-events [:api.notes/saved])
     :ok-commands  (conj ok-commands [:toasts/succeed! {:message "note updated"}])
     :err-events   [[:resources/failed [::notes#update resource-id] :remote]]
     :err-commands [[:toasts/fail!]]}))

(def ^:private new-schedule-validator
  (valid/->validator valid/new-schedule))

(defmethod resource-spec ::schedules#create
  [{[_ resource-id] ::spec {:keys [data reset-to]} :params}]
  (if-let [errors (new-schedule-validator data)]
    {:pre-events [[:resources/failed [::schedules#create resource-id] :local errors]]}
    (let [ok-events (if reset-to
                      [[:forms/created resource-id reset-to]
                       [:resources/destroyed [::schedules#create resource-id]]]
                      [[:resources/succeeded [::schedules#create resource-id]]])]
      {:route        :routes.api/schedules
       :method       :post
       :body         data
       :ok-events    (conj ok-events [:api.schedules/saved (:schedules/note-id data)])
       :ok-commands  [[:toasts/succeed! {:message "schedule created"}]]
       :err-events   [[:resources/failed [::schedules#create resource-id] :remote]]
       :err-commands [[:toasts/fail!]]})))
