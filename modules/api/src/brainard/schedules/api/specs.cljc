(ns brainard.schedules.api.specs
  (:require
    [malli.util :as mu]))

(def create
  [:and
   [:map
    [:schedules/note-id uuid?]
    [:schedules/after-timestamp {:optional true} inst?]
    [:schedules/before-timestamp {:optional true} inst?]
    [:schedules/day {:optional true} int?]
    [:schedules/week-index {:optional true} int?]
    [:schedules/month {:optional true} [:fn {:error/message "invalid month"}
                                        #{:january
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
                                          :december}]]
    [:schedules/weekday {:optional true} [:fn {:error/message "invalid weekday"}
                                          #{:sunday
                                            :monday
                                            :tuesday
                                            :wednesday
                                            :thursday
                                            :friday
                                            :saturday}]]]
   [:fn {:error/message "must select at least one filter"}
    (some-fn (comp some? :schedules/after-timestamp)
             (comp some? :schedules/before-timestamp)
             (comp some? :schedules/month)
             (comp some? :schedules/day)
             (comp some? :schedules/weekday)
             (comp some? :schedules/week-index))]
   [:fn {:error/message "cannot select both \"day of month\" and \"week of month\""}
    (some-fn (comp nil? :schedules/day)
             (comp nil? :schedules/week-index))]
   [:fn {:error/message "\"earliest moment\" has to be before \"latest moment\""}
    (fn [{:schedules/keys [after-timestamp before-timestamp]}]
      (if (and after-timestamp before-timestamp)
        (<= (.getTime after-timestamp) (.getTime before-timestamp))
        true))]])

(def full
  (mu/merge (second create)
            [:map
             [:schedules/id uuid?]]))
