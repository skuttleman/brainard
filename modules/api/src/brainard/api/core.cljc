(ns brainard.api.core
  (:require
   [brainard.api.events.core :as events]
   [brainard.api.validations :as valid]
   [brainard.api.utils.logger :as log]
   [brainard.attachments.api.core :as api.attachments]
   [brainard.notes.api.core :as api.notes]
   [brainard.schedules.api.core :as api.sched]
   [brainard.workspace.api.core :as api.ws]))

(defmulti ^:private invoke-api* (fn [api _ _] api))

(defn links-exist! [apis {note-id :notes/id :notes/keys [links]}]
  (when (seq links)
    (let [note-ids (into #{} (map :notes/id) links)
          existing-ids (into #{}
                             (map :notes/id)
                             (api.notes/get-notes (:notes apis) {:notes/ids note-ids}))]
      (when-not (= note-ids existing-ids)
        (throw (ex-info "note cannot be linked to unavailable note"
                        {::valid/type ::valid/input-validation
                         :details     {:reason "note cannot be linked to unavailable note"}})))

      (when (some #{note-id} note-ids)
        (throw (ex-info "note cannot be linked to itself"
                        {::valid/type ::valid/input-validation
                         :details     {:reason "note cannot be linked to itself"}}))))))

(defmethod invoke-api* :api.notes/create!
  [_ apis note]
  (links-exist! apis note)
  (doto (api.notes/create! (:notes apis) note)
    (->> (api.notes/search-create! (:notes apis)))))

(defmethod invoke-api* :api.notes/update!
  [_ apis note]
  (links-exist! apis note)
  (doto (api.notes/update! (:notes apis) (:notes/id note) note)
    (->> (api.notes/search-update! (:notes apis)))))

(defmethod invoke-api* :api.notes/reinstate!
  [_ apis note]
  (when-let [pre (api.notes/get-as-of (:notes apis)
                                      (:notes/id note)
                                      (:notes/history-id note))]
    (let [updated (-> note
                      (merge pre)
                      (dissoc :notes/links :notes/old-links))]
      (invoke-api* :api.notes/update! apis updated))))

(defmethod invoke-api* :api.notes/delete!
  [_ apis {note-id :notes/id}]
  (api.notes/delete! (:notes apis) [note-id])
  (api.sched/delete-for-notes! (:schedules apis) [note-id])
  (api.notes/search-delete! (:notes apis) [note-id])
  nil)

(defmethod invoke-api* :api.notes/bulk-delete!
  [_ apis {note-ids :notes/ids}]
  (api.notes/delete! (:notes apis) note-ids)
  (api.sched/delete-for-notes! (:schedules apis) note-ids)
  (api.notes/search-delete! (:notes apis) note-ids)
  nil)

(defmethod invoke-api* :api.notes/select
  [_ apis {:notes/keys [body] :as params}]
  (if-let [note-ids (some->> body
                             (hash-map :notes/body)
                             (api.notes/search-notes (:notes apis))
                             (map :notes/id)
                             seq)]
    (api.notes/get-notes (:notes apis) (assoc params :notes/ids note-ids))
    (if (seq (dissoc params :notes/body))
      (api.notes/get-notes (:notes apis) params)
      ())))

(defmethod invoke-api* :api.notes/fetch
  [_ apis {note-id :notes/id}]
  (api.notes/get-note (:notes apis) note-id))

(defmethod invoke-api* :api.notes/fetch?history
  [_ apis {note-id :notes/id}]
  (api.notes/get-note-history (:notes apis) note-id))

(defmethod invoke-api* :api.tags/select
  [_ apis _]
  (api.notes/get-tags (:notes apis)))

(defmethod invoke-api* :api.contexts/select
  [_ apis _]
  (api.notes/get-contexts (:notes apis)))

(defmethod invoke-api* :api.schedules/select
  [_ apis {note-id :notes/id}]
  (api.sched/get-by-note-id (:schedules apis) note-id))

(defmethod invoke-api* :api.schedules/create!
  [_ apis {:schedules/keys [note-id] :as schedule}]
  (api.sched/create! (:schedules apis) schedule)
  (api.sched/get-by-note-id (:schedules apis) note-id))

(defmethod invoke-api* :api.schedules/delete!
  [_ apis {schedule-id :schedules/id}]
  (if-let [{:schedules/keys [note-id]} (api.sched/get-by-id (:schedules apis) schedule-id)]
    (do (api.sched/delete! (:schedules apis) schedule-id)
        (api.sched/get-by-note-id (:schedules apis) note-id))
    ()))

(defmethod invoke-api* :api.notes/relevant
  [_ apis {:keys [timestamp]}]
  (if-let [ids (->> (api.sched/relevant-schedules (:schedules apis) timestamp)
                    (map :schedules/note-id)
                    seq)]
    (api.notes/get-notes (:notes apis) {:notes/ids ids})
    ()))

(defmethod invoke-api* :api.workspace-nodes/select-tree
  [_ apis _]
  (api.ws/get-tree (:workspace apis)))

(defmethod invoke-api* :api.workspace-nodes/create!
  [_ apis {:keys [request-id] :as node}]
  (api.ws/create! (:workspace apis) node)
  (let [result (invoke-api* :api.workspace-nodes/select-tree apis nil)]
    (events/broadcast! (:events apis) :workspace/tree {:request-id request-id :data result})
    ::no-content))

(defmethod invoke-api* :api.workspace-nodes/delete!
  [_ apis node]
  (api.ws/delete! (:workspace apis) (:workspace-nodes/id node))
  (invoke-api* :api.workspace-nodes/select-tree apis nil))

(defmethod invoke-api* :api.workspace-nodes/update!
  [_ apis node]
  (api.ws/update! (:workspace apis) (:workspace-nodes/id node) node)
  (invoke-api* :api.workspace-nodes/select-tree apis nil))

(defmethod invoke-api* :api.attachments/upload!
  [_ apis {:keys [attachments]}]
  (api.attachments/upload! (:attachments apis) attachments))

(def ^:private missing-spec
  (memoize (fn [api]
             (log/warn "no input-spec defined for API:" api))))

(defn invoke-api
  "Validate input for an API and invoke the corresponding handler, returning the result.
   Call this fn instead of calling invoke-api* directly."
  [api apis input]
  (if-let [input-spec (valid/input-specs api)]
    (valid/validate! input-spec input ::valid/input-validation)
    (missing-spec api))
  (when-let [result (invoke-api* api apis input)]
    (when (not= result ::no-content)
      (when-let [output-spec (valid/output-specs api)]
        (let [validator (valid/->validator output-spec)]
          (when-let [errors (validator result)]
            (throw (ex-info "failed to produce valid output" {:api api :errors errors}))))))
    result))
