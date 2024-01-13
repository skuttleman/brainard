(ns brainard.infra.validations
  "Specs for data that flows through the system."
  (:require
    [malli.core :as m]
    [brainard.notes.api.specs :as snotes]
    [brainard.schedules.api.specs :as ssched]
    [malli.error :as me]))

(def api-errors
  [:map
   [:errors
    [:sequential
     [:map
      [:message string?]
      [:code keyword?]]]]])

(def input-specs
  {[:get :routes.api/notes]       snotes/query
   [:post :routes.api/notes]      snotes/create
   [:get :routes.api/note]        [:map [:notes/id uuid?]]
   [:patch :routes.api/note]      snotes/modify

   [:post :routes.api/schedules]  ssched/create
   [:delete :routes.api/schedule] [:map [:schedules/id uuid?]]})

(def output-specs
  {[:get :routes.api/notes]      [:map [:data [:sequential snotes/full]]]
   [:get :routes.api/note]       [:map [:data snotes/full]]
   [:post :routes.api/notes]     [:map [:data snotes/full]]
   [:patch :routes.api/note]     [:map [:data snotes/full]]
   [:get :routes.api/tags]       [:map [:data [:set keyword?]]]
   [:get :routes.api/contexts]   [:map [:data [:set string?]]]

   [:post :routes.api/schedules] [:map [:data ssched/full]]})

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
