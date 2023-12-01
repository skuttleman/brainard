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

(def notes-query
  [:and
   [:map
    [:notes/context {:optional true} string?]
    [:notes/tags {:optional true} [:set keyword?]]]
   [:fn {:error/message "must select at least one context or tag"}
    (some-fn (comp seq :notes/tags)
             (comp some? :notes/context))]])

(def input-specs
  {[:get :routes.api/notes]  notes-query
   [:post :routes.api/notes] new-note
   [:get :routes.api/note]   [:map [:notes/id uuid?]]
   [:patch :routes.api/note] update-note})

(def output-specs
  {[:get :routes.api/notes]    [:map [:data [:sequential full-note]]]
   [:get :routes.api/note]     [:map [:data full-note]]
   [:post :routes.api/notes]   [:map [:data full-note]]
   [:patch :routes.api/note]   [:map [:data full-note]]
   [:get :routes.api/tags]     [:map [:data [:set keyword?]]]
   [:get :routes.api/contexts] [:map [:data [:set string?]]]})
