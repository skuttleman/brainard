(ns brainard.infra.views.pages.note.schedules
  (:require
   [brainard.api.utils.dates :as dates]
   [brainard.api.validations :as valid]
   [brainard.infra.store.specs :as specs]
   [brainard.schedules.api.specs :as ssched]
   [clojure.pprint :as pp]
   [defacto.forms.core :as-alias forms]
   [defacto.forms.plus :as forms+]
   [defacto.resources.core :as res]))

(defn ->sync-key [note-id] [::schedules#sync note-id])
(defn ->create-key [note-id] [::forms+/valid (->sync-key note-id) [::forms/edit-schedule]])

(defn ^:private ->schedule-spec [sync-key spec-key spec success-msg]
  (specs/with-cbs (res/->request-spec spec-key spec)
                  :ok-commands [[:toasts/succeed! {:message success-msg}]
                                [::res/resubmit! sync-key]]
                  :err-commands [[:toasts/fail!]]))

(forms+/validated ::schedules#sync (valid/->validator ssched/create)
  [[_ note-id :as res-key] {::forms/keys [data] :as spec}]
  (case (::action spec)
    ::delete (->schedule-spec res-key
                              [::specs/schedules#destroy (:schedules/id spec)]
                              spec
                              "schedule deleted")
    ::create (let [spec (assoc spec :payload (valid/select-spec-keys data ssched/create))]
               (->schedule-spec res-key
                                [::specs/schedules#create (:schedules/id spec)]
                                spec
                                "schedule created"))
    (res/->request-spec [::specs/schedules#select note-id] spec)))

(def ^:const month-options
  (into [[nil "(any)"]]
        (map (juxt identity name))
        [:january
         :february
         :march
         :april
         :may
         :june
         :july
         :august
         :september
         :october
         :november
         :december]))

(def ^:const weekday-options
  (into [[nil "(any)"]]
        (map (juxt identity name))
        [:sunday
         :monday
         :tuesday
         :wednesday
         :thursday
         :friday
         :saturday]))

(def ^:const day-options
  (into [[nil "(any)"]]
        (map (juxt identity identity))
        (range 1 32)))

(defn ^:private ->radix [v]
  (pp/cl-format nil "~:R" v))

(def ^:const week-index-options
  (into [[nil "(any)"]]
        (map (juxt identity #(str (->radix (inc %)) " week")))
        (range 5)))

(defn ^:private ->schedule-part [[k v]]
  (case k
    :schedules/weekday [:<>
                        [:span "on a"]
                        [:em.blue (name v)]]

    :schedules/month [:<>
                      [:span "during"]
                      [:em.blue (name v)]]

    :schedules/day [:<>
                    [:span "on the"]
                    [:em.blue (->radix v)]
                    [:span "day of the month"]]

    :schedules/week-index [:<>
                           [:span "during the"]
                           [:em.blue (->radix (inc v))]
                           [:span "week of the month"]]
    :schedules/before-timestamp [:<>
                                 [:span [:em.blue "before"]]
                                 [:span (dates/to-iso-datetime-min-precision v)]]
    :schedules/after-timestamp [:<>
                                [:span [:em.blue "after"]]
                                [:span (dates/to-iso-datetime-min-precision v)]]
    nil))

(def ^:private schedule-order
  {:schedules/day              0
   :schedules/weekday          1
   :schedules/month            2
   :schedules/week-index       3
   :schedules/after-timestamp  4
   :schedules/before-timestamp 5})

(defn schedule-parts
  "Return a sequence of hiccup display parts for non-empty schedule fields in form-data."
  [form-data]
  (->> form-data
       (sort-by (comp schedule-order key))
       (keep ->schedule-part)
       seq))

(defn ->delete-sched-modal
  "Return modal data for confirming deletion of a schedule."
  [note-id sched-id]
  [:modals/sure?
   {:description   "This schedule will be deleted"
    :yes-btn-class ["delete-schedule"]
    :yes-commands  [[::res/resubmit!
                     (->sync-key note-id)
                     {::action      ::delete
                      :schedules/id sched-id}]]}])
