(ns brainard.common.validations.core
  "Specs for data that flows through the system."
  (:require
    [malli.core :as m]
    [malli.error :as me]
    [malli.util :as mu]))

(def api-errors
  [:map
   [:errors
    [:sequential
     [:map
      [:message string?]
      [:code keyword?]]]]])


;; notes
(def new-note
  [:map
   [:notes/context string?]
   [:notes/body string?]
   [:notes/tags {:optional true} [:set keyword?]]])

(def full-note
  (mu/merge new-note
            [:map
             [:notes/id uuid?]
             [:notes/timestamp inst?]]))

(def update-note
  [:map
   [:notes/context {:optional true} string?]
   [:notes/body {:optional true} string?]
   [:notes/tags {:optional true} [:set keyword?]]
   [:notes/tags!remove {:optional true} [:set keyword?]]])

(def notes-query
  [:and
   [:map
    [:notes/context {:optional true} string?]
    [:notes/tags {:optional true} [:set keyword?]]]
   [:fn {:error/message "must select at least one context or tag"}
    (some-fn (comp seq :notes/tags)
             (comp some? :notes/context))]])


;; schedules
(def new-schedule
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
             (comp some? :schedules/week-index))]])

(def full-schedule
  (mu/merge (second new-schedule)
            [:map
             [:schedules/id uuid?]]))


(def input-specs
  {[:get :routes.api/notes]       notes-query
   [:post :routes.api/notes]      new-note
   [:get :routes.api/note]        [:map [:notes/id uuid?]]
   [:patch :routes.api/note]      update-note

   [:post :routes.api/schedules]  new-schedule
   [:delete :routes.api/schedule] [:map [:schedules/id uuid?]]})

(def output-specs
  {[:get :routes.api/notes]      [:map [:data [:sequential full-note]]]
   [:get :routes.api/note]       [:map [:data full-note]]
   [:post :routes.api/notes]     [:map [:data full-note]]
   [:patch :routes.api/note]     [:map [:data full-note]]
   [:get :routes.api/tags]       [:map [:data [:set keyword?]]]
   [:get :routes.api/contexts]   [:map [:data [:set string?]]]

   [:post :routes.api/schedules] [:map [:data full-schedule]]})

(defn ^:private throw! [type params]
  (throw (ex-info "failed spec validation" (assoc params ::type type))))

(defn ->validator
  "Creates function that validates a value against a spec.

   (def malli-spec
     [:map
      [:first-name string?]
      [:last-name string?]])

   (def validator (->validator malli-spec))

   (validator {:first-name 3})
   ;; => {:first-name [\"must be a string\"] :last-name [\"missing\"]}"
  [spec]
  (fn [data]
    (when-let [errors (m/explain spec data)]
      (me/humanize errors))))

(defn validate!
  "Validates a value against a spec and throws an exception with ::type `type`."
  [spec data type]
  (let [validator (->validator spec)]
    (when-let [details (validator data)]
      (throw! type {:data data :details details}))))
