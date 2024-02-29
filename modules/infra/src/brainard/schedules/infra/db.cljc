(ns brainard.schedules.infra.db
  (:require
    [brainard.schedules.api.core :as api.sched]
    [brainard.api.storage.interfaces :as istorage]))

(def ^:private select
  '[:find (pull ?e [*])
    :in $])

(defmethod istorage/->input ::api.sched/save!
  [schedule]
  [(select-keys schedule #{:schedules/id
                           :schedules/note-id
                           :schedules/before-timestamp
                           :schedules/after-timestamp
                           :schedules/month
                           :schedules/day
                           :schedules/weekday
                           :schedules/week-index})])

(defmethod istorage/->input ::api.sched/delete!
  [{schedule-id :schedules/id}]
  [[:db/retractEntity [:schedules/id schedule-id]]])

(defmethod istorage/->input ::api.sched/schedules
  [{:schedules/keys [after-timestamp before-timestamp day month week-index weekday]}]
  {:query (into select
                '[?weekday ?month ?day ?week-idx ?after ?before
                  :where
                  [?e :schedules/id]
                  [(get-else $ ?e :schedules/weekday ?weekday) ?wd]
                  [(= ?wd ?weekday)]
                  [(get-else $ ?e :schedules/month ?month) ?m]
                  [(= ?m ?month)]
                  [(get-else $ ?e :schedules/day ?day) ?d]
                  [(= ?d ?day)]
                  [(get-else $ ?e :schedules/week-index ?week-idx) ?wi]
                  [(= ?wi ?week-idx)]
                  [(get-else $ ?e :schedules/after-timestamp ?after) ?ats]
                  [(<= ?ats ?after)]
                  [(get-else $ ?e :schedules/before-timestamp ?before) ?bts]
                  [(>= ?bts ?before)]])
   :args [weekday month day week-index after-timestamp before-timestamp]
   :xform (map first)})

(defmethod istorage/->input ::api.sched/get-by-note-id
  [{:schedules/keys [note-id]}]
  {:query (into select
                '[?note-id
                  :where
                  [?e :schedules/note-id ?note-id]])
   :args [note-id]
   :xform (map first)})
