(ns brainard.api.core
  (:require
    [brainard.api.validations :as valid]
    [brainard.applications.api.core :as api.apps]
    [brainard.api.utils.logger :as log]
    [brainard.notes.api.core :as api.notes]
    [brainard.schedules.api.core :as api.sched]
    [brainard.workspace.api.core :as api.ws]))

(defmulti ^:private invoke-api* (fn [api _ _] api))

(defmethod invoke-api* :api.notes/create!
  [_ apis note]
  (api.notes/create! (:notes apis) note))

(defmethod invoke-api* :api.notes/update!
  [_ apis note]
  (api.notes/update! (:notes apis) (:notes/id note) note))

(defmethod invoke-api* :api.notes/delete!
  [_ apis {note-id :notes/id}]
  (api.notes/delete! (:notes apis) note-id)
  (api.sched/delete-for-note! (:schedules apis) note-id)
  nil)

(defmethod invoke-api* :api.notes/select
  [_ apis params]
  (api.notes/get-notes (:notes apis) params))

(defmethod invoke-api* :api.notes/fetch
  [_ apis {note-id :notes/id}]
  (when-let [note (api.notes/get-note (:notes apis) note-id)]
    (let [schedules (api.sched/get-by-note-id (:schedules apis) note-id)]
      (assoc note :notes/schedules schedules))))

(defmethod invoke-api* :api.notes/fetch?history
  [_ apis {note-id :notes/id}]
  (api.notes/get-note-history (:notes apis) note-id))

(defmethod invoke-api* :api.tags/select
  [_ apis _]
  (api.notes/get-tags (:notes apis)))

(defmethod invoke-api* :api.contexts/select
  [_ apis _]
  (api.notes/get-contexts (:notes apis)))

(defmethod invoke-api* :api.schedules/create!
  [_ apis schedule]
  (api.sched/create! (:schedules apis) schedule))

(defmethod invoke-api* :api.schedules/delete!
  [_ apis {schedule-id :schedules/id}]
  (api.sched/delete! (:schedules apis) schedule-id)
  nil)

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
  [_ apis node]
  (api.ws/create! (:workspace apis) node))

(defmethod invoke-api* :api.workspace-nodes/delete!
  [_ apis node]
  (api.ws/delete! (:workspace apis) (:workspace-nodes/id node)))

(defmethod invoke-api* :api.workspace-nodes/update!
  [_ apis node]
  (api.ws/update! (:workspace apis) (:workspace-nodes/id node) node))

(defmethod invoke-api* :api.applications/create!
  [_ apis app]
  (api.apps/create! (:applications apis) app))

(defmethod invoke-api* :api.applications/fetch
  [_ apis {app-id :applications/id}]
  (api.apps/fetch (:applications apis) app-id))

(def ^:private missing-spec
  (memoize (fn [api]
             (log/warn "no input-spec defined for API:" api))))

(defn invoke-api [api apis input]
  (if-let [input-spec (valid/input-specs api)]
    (valid/validate! input-spec input ::valid/input-validation)
    (missing-spec api))
  (let [result (invoke-api* api apis input)]
    (when-let [output-spec (valid/output-specs api)]
      (let [validator (valid/->validator output-spec)]
        (when-let [errors (validator result)]
          (log/error "failed to produce valid output" {:errors errors :api api}))))
    result))
