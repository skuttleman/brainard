(ns brainard.infra.views.pages.home
  "The home page with note creation form."
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.pages.interfaces :as ipages]
    [brainard.notes.infra.views :as notes.views]
    [clojure.string :as string]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(def ^:private ^:const form-id ::forms/new-note)
(def ^:private ^:const create-note-key [::forms+/valid [::specs/notes#create form-id]])
(def ^:private ^:const select-notes-key [::specs/notes#select form-id])

(def ^:private new-note
  {:notes/body    nil
   :notes/context nil
   :notes/pinned? false
   :notes/tags    #{}})

(defn ^:private ->context-blur [*:store sub:form]
  (fn [_]
    (let [{:notes/keys [context]} (forms/data @sub:form)]
      (when-not (string/blank? context)
        (store/dispatch! *:store
                         [::res/sync! select-notes-key {::forms/data {:notes/context context}}])))))

(defn ^:private search-results [_attrs notes]
  [:div
   (if (seq notes)
     [:<>
      [:h3.subtitle [:em "Some related notes..."]]
      [notes.views/note-list {} notes]]
     [:em "Brand new topic!"])])

(defmethod ipages/page :routes.ui/home
  [*:store _route-info]
  (r/with-let [sub:form+ (-> *:store
                             (store/dispatch! [::forms/ensure! create-note-key new-note])
                             (store/subscribe [::forms+/?:form+ create-note-key]))
               sub:contexts (store/subscribe *:store [::res/?:resource [::specs/contexts#select]])
               sub:tags (store/subscribe *:store [::res/?:resource [::specs/tags#select]])
               sub:notes (store/subscribe *:store [::res/?:resource select-notes-key])
               on-context-blur (->context-blur *:store sub:form+)]
    [:div
     [notes.views/note-form
      {:*:store         *:store
       :form+           @sub:form+
       :on-context-blur on-context-blur
       :params          {:pre-events [[::res/destroyed select-notes-key]]}
       :resource-key    create-note-key
       :sub:contexts    sub:contexts
       :sub:tags        sub:tags}]
     [comp/with-resource sub:notes [search-results {:hide-init? true}]]]
    (finally
      (store/emit! *:store [::forms+/destroyed create-note-key])
      (store/emit! *:store [::res/destroyed select-notes-key]))))
