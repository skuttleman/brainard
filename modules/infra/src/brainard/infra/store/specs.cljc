(ns brainard.infra.store.specs
  (:require
    [clojure.set :as set]
    [defacto.resources.core :as res]))

(defn ^:private with-msgs [m k params spec]
  (if-let [v (seq (concat (get spec k) (get params k) (get-in spec [:params k])))]
    (assoc m k (vec v))
    m))

(defn ^:private ->req [{:keys [route] :as params} spec]
  (let [{:keys [query-params] :as route-params} (:params params)
        req-params (-> params
                       (select-keys #{:body :headers :multipart-params})
                       (assoc :request-method (:method params)
                              :route {:token        route
                                      :route-params (dissoc route-params :query-params)
                                      :query-params query-params}))]
    (-> {:params req-params
         :->ok   :data
         :->err  :errors}
        (with-msgs :pre-events params spec)
        (with-msgs :pre-commands params spec)
        (with-msgs :ok-events params spec)
        (with-msgs :ok-commands params spec)
        (with-msgs :err-events params spec)
        (with-msgs :err-commands params spec)
        (with-msgs :prog-events params spec)
        (with-msgs :prog-commands params spec))))

(defn with-cbs [spec & kvs]
  (->> kvs
       (partition-all 2)
       (reduce (fn [spec [k cbs]]
                 (update spec k into cbs))
               spec)))

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
  [_ {:keys [params] :as spec}]
  (->req {:route  :routes.api/notes
          :method :get
          :params {:query-params params}}
         spec))

(defmethod res/->request-spec ::notes#find
  [[_ resource-id] spec]
  (->req {:route  :routes.api/note
          :method :get
          :params {:notes/id resource-id}}
         spec))

(defmethod res/->request-spec ::notes#create
  [_ {:keys [payload] :as spec}]
  (->req {:route  :routes.api/notes
          :method :post
          :body   payload}
         spec))

(defn ^:private diff-tags [old curr]
  (when (or old curr)
    (let [removals (set/difference old curr)]
      {:notes/tags!remove (or removals #{})
       :notes/tags        (or curr #{})})))

(defn ^:private diff-attachments [old curr]
  (when (or old curr)
    (letfn [(id-set [xs]
              (into #{}
                    (map :attachments/id)
                    xs))]
      (let [removals (set (set/difference (id-set old) (id-set curr)))
            updates (into #{}
                          (map #(select-keys % #{:attachments/id :attachments/name}))
                          curr)]
        {:notes/attachments!remove removals
         :notes/attachments        updates}))))

(defmethod res/->request-spec ::notes#modify
  [[_ resource-id] {:keys [payload prev-attachments prev-tags] :as spec}]
  (->req {:route  :routes.api/note
          :params {:notes/id resource-id}
          :method :patch
          :body   (-> payload
                      (select-keys #{:notes/context
                                     :notes/pinned?
                                     :notes/body})
                      (merge (diff-tags prev-tags (:notes/tags payload))
                             (diff-attachments prev-attachments (:notes/attachments payload))))}
         spec))

(defmethod res/->request-spec ::notes#destroy
  [[_ note-id] spec]
  (->req {:route  :routes.api/note
          :params {:notes/id note-id}
          :method :delete}
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
  (->req {:route  :routes.api/schedules
          :method :post
          :body   payload}
         spec))

(defmethod res/->request-spec ::schedules#destroy
  [[_ resource-id] spec]
  (->req {:route  :routes.api/schedule
          :method :delete
          :params {:schedules/id resource-id}}
         spec))

(defmethod res/->request-spec ::workspace#select
  [_ spec]
  (->req {:route  :routes.api/workspace-nodes
          :method :get}
         spec))

(defmethod res/->request-spec ::workspace#create
  [_ {:keys [payload] :as spec}]
  (->req {:route  :routes.api/workspace-nodes
          :method :post
          :body   payload}
         spec))

(defmethod res/->request-spec ::workspace#modify
  [[_ resource-id] {:keys [payload] :as spec}]
  (->req {:route  :routes.api/workspace-node
          :method :patch
          :params {:workspace-nodes/id resource-id}
          :body   payload}
         spec))

(defmethod res/->request-spec ::workspace#destroy
  [[_ resource-id] spec]
  (->req {:route  :routes.api/workspace-node
          :method :delete
          :params {:workspace-nodes/id resource-id}}
         spec))

(defmethod res/->request-spec ::attachment#upload
  [_ spec]
  (->req {:route            :routes.api/attachments
          :method           :post
          :multipart-params (for [file (:files spec)]
                              ["files[]" file])}
         spec))
