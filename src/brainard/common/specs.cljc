(ns brainard.common.specs
  (:require
    [malli.core :as m]
    [malli.error :as me]
    [malli.util :as mu]))

(def errors
  [:map
   [:errors
    [:sequential
     [:map
      [:message string?]
      [:code keyword?]]]]])

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
   [:notes.retract/tags {:optional true} [:set keyword?]]])

(def input-specs
  {[:get :routes.api/notes]  [:map
                              [:notes/contexts [:set string?]]
                              [:notes/tags [:set keyword?]]]
   [:post :routes.api/notes] new-note
   [:patch :routes.api/note] update-note})

(def output-specs
  {[:get :routes.api/notes]    [:map [:data [:sequential full-note]]]
   [:post :routes.api/notes]   [:map [:data full-note]]
   [:patch :routes.api/note]   [:map [:data full-note]]
   [:get :routes.api/tags]     [:map [:data [:set keyword?]]]
   [:get :routes.api/contexts] [:map [:data [:set string?]]]})

(defn ->validator [spec]
  (fn [data]
    (when-let [errors (m/explain spec data)]
      (me/humanize errors))))
