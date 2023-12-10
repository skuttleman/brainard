(ns brainard.common.resources.specs
  (:require
    [brainard.common.utils.colls :as colls]
    [brainard.common.utils.routing :as rte]))

(defmulti ^:private resource-spec (comp first colls/wrap-vector ::spec))

(defn ->req [spec]
  (let [params (resource-spec spec)
        url (rte/path-for (:route params) (:params params))]
    (merge {:req {:request-method (:method params)
                  :url            url
                  :body           (some-> (:body params) pr-str)
                  :headers        {"content-type" "application/edn"}}}
           (select-keys params #{:pre-events
                                 :pre-commands
                                 :ok-events
                                 :ok-commands
                                 :err-events
                                 :err-commands}))))

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

(defmethod resource-spec ::notes#select
  [{[_ resource-id] ::spec {:keys [data pre-commands]} :params}]
  {:route        :routes.api/notes
   :method       :get
   :params       {:query-params data}
   :pre-commands pre-commands
   :ok-events    [[:resources/succeeded [::notes#select resource-id]]]
   :err-events   [[:resources/failed [::notes#select resource-id] :remote]]})

(defmethod resource-spec ::notes#find
  [{[_ resource-id] ::spec}]
  {:route      :routes.api/note
   :method     :get
   :params     {:notes/id resource-id}
   :ok-events  [[:resources/succeeded [::notes#find resource-id]]]
   :err-events [[:resources/failed [::notes#find resource-id] :remote]]})

(defmethod resource-spec ::notes#create
  [{[_ resource-id] ::spec {:keys [data pre-events reset-to]} :params}]
  (let [ok-events (if reset-to
                    [[:forms/created resource-id reset-to]
                     [:resources/destroyed [::notes#create resource-id]]]
                    [[:resources/succeeded [::notes#create resource-id]]])]
    {:route        :routes.api/notes
     :method       :post
     :body         data
     :pre-events   pre-events
     :ok-events    (conj ok-events [:resources/note-saved])
     :ok-commands  [[:toasts/succeed! {:message "note created"}]]
     :err-events   [[:resources/failed [::notes#create resource-id] :remote]]
     :err-commands [[:toasts/fail!]]}))

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
     :ok-events    (conj ok-events [:resources/note-saved])
     :ok-commands  (conj ok-commands [:toasts/succeed! {:message "note updated"}])
     :err-events   [[:resources/failed [::notes#update resource-id] :remote]]
     :err-commands [[:toasts/fail!]]}))

(defmethod resource-spec ::schedules#create
  [{[_ resource-id] ::spec {:keys [data reset-to]} :params}]
  (let [ok-events (if reset-to
                    [[:forms/created resource-id reset-to]
                     [:resources/destroyed [::schedules#create resource-id]]]
                    [[:resources/succeeded [::schedules#create resource-id]]])]
    {:route        :routes.api/schedules
     :method       :post
     :body         data
     :ok-events    (conj ok-events [:resources/schedule-saved])
     :ok-commands  [[:toasts/succeed! {:message "schedule created"}]]
     :err-events   [[:resources/failed [::schedules#create resource-id] :remote]]
     :err-commands [[:toasts/fail!]]}))
