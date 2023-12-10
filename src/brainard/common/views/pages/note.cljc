(ns brainard.common.views.pages.note
  "The page for viewing a note and editing its tags."
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.store.core :as store]
    [brainard.common.resources.specs :as-alias rspecs]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.uuids :as uuids]
    [brainard.common.validations.core :as valid]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]
    [clojure.set :as set]))

(def ^:private ^:const form-id
  ::forms/edit-note)

(def ^:private new-schedule-validator
  (valid/->validator valid/new-schedule))

(defn ^:private diff-tags [old new]
  (let [removals (set/difference old new)]
    {:notes/tags!remove removals
     :notes/tags        new}))

(defn ^:private tag-editor [{:keys [*:store form sub:res sub:tags]} note]
  (let [data (forms/data form)
        cancel-event [:forms/created form-id {:notes/tags (:notes/tags note)
                                              ::editing?  false}]]
    [ctrls/form {:*:store      *:store
                 :form         form
                 :params       {:note-id  (:notes/id note)
                                :old      note
                                :data     (diff-tags (:notes/tags note) (:notes/tags data))
                                :fetch?   true
                                :reset-to (assoc data ::editing? false)}
                 :resource-key [::rspecs/notes#update form-id]
                 :sub:res      sub:res
                 :submit/body  "Save"
                 :buttons      [[:button.button.is-cancel
                                 {:on-click (fn [e]
                                              (dom/prevent-default! e)
                                              (store/emit! *:store cancel-event))}
                                 "Cancel"]]}
     [ctrls/tags-editor (-> {:*:store   *:store
                             :label     "Tags"
                             :sub:items sub:tags}
                            (ctrls/with-attrs form
                                              sub:res
                                              [:notes/tags]))]]))

(defn ^:private tag-list [{:keys [*:store]} note]
  [:div.layout--space-between
   (if-let [tags (not-empty (:notes/tags note))]
     [comp/tag-list {:value tags}]
     [:em "no tags"])
   [:button.button {:disabled #?(:clj true :default false)
                    :on-click (fn [_]
                                (store/emit! *:store [:forms/changed form-id [::editing?] true]))}
    "edit tags"]])

(def ^:private ^:const month-options
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

(def ^:private ^:const weekday-options
  (into [[nil "(any)"]]
        (map (juxt identity name))
        [:sunday
         :monday
         :tuesday
         :wednesday
         :thursday
         :friday
         :saturday]))

(def ^:private ^:const day-options
  (into [[nil "(any)"]]
        (map (juxt identity identity))
        (range 1 32)))

(def ^:private ^:const week-index-options
  [[nil "(any)"]
   [0 "1st week"]
   [1 "2nd week"]
   [2 "3rd week"]
   [3 "4th week"]
   [4 "5th week"]])

(defn ^:private schedules-editor [{:keys [*:store]} note]
  (r/with-let [init-form {:schedules/note-id (:notes/id note)}
               form-id (uuids/random)
               sub:form (do (store/dispatch! *:store [:forms/ensure! form-id init-form {:remove-nil? true}])
                            (store/subscribe *:store [:forms/?:form form-id]))
               sub:res (store/subscribe *:store [:resources/?:resource [::rspecs/schedules#create form-id]])]
    (let [form @sub:form
          form-data (forms/data form)
          errors (new-schedule-validator form-data)]
      [ctrls/form {:*:store      *:store
                   :form         form
                   :errors       errors
                   :params       {:reset-to init-form
                                  :data     form-data}
                   :resource-key [::rspecs/schedules#create form-id]
                   :sub:res      sub:res
                   :submit/body  "Save"}
       [:p.subtitle "Add a schedule"]
       [ctrls/select (-> {:*:store *:store
                          :label   "Day of the month"}
                         (ctrls/with-attrs form sub:res [:schedules/day] errors))
        day-options]
       [ctrls/select (-> {:*:store *:store
                          :label   "Day of the week"}
                         (ctrls/with-attrs form sub:res [:schedules/weekday] errors))
        weekday-options]
       [ctrls/select (-> {:*:store *:store
                          :label   "Week of the month"}
                         (ctrls/with-attrs form sub:res [:schedules/week-index] errors))
        week-index-options]
       [ctrls/select (-> {:*:store *:store
                          :label   "Month of the year"}
                         (ctrls/with-attrs form sub:res [:schedules/month] errors))
        month-options]])
    (finally
      (store/emit! *:store [:forms/destroyed form-id])
      (store/emit! *:store [:resources/destroyed [::rspecs/schedules#create form-id]]))))

(defn ^:private root* [*:store [note]]
  (r/with-let [init-form {:notes/tags (:notes/tags note)
                          ::editing?  false}
               sub:form (do (store/dispatch! *:store [:forms/ensure! form-id init-form])
                            (store/subscribe *:store [:forms/?:form form-id]))
               sub:res (store/subscribe *:store [:resources/?:resource [::rspecs/notes#update form-id]])
               sub:tags (store/subscribe *:store [:resources/?:resource ::rspecs/tags#select])]
    (let [form @sub:form
          attrs {:*:store  *:store
                 :form     form
                 :sub:res  sub:res
                 :sub:tags sub:tags}]
      [:div.layout--stack-between
       [:h1 [:strong (:notes/context note)]]
       [:p (:notes/body note)]
       (if (::editing? (forms/data form))
         [tag-editor attrs note]
         [tag-list attrs note])
       [schedules-editor attrs note]])
    (finally
      (store/emit! *:store [:forms/destroyed form-id]))))

(defmethod ipages/page :routes.ui/note
  [{:keys [route-params *:store]}]
  (let [resource [::rspecs/notes#find (:notes/id route-params)]]
    (r/with-let [sub:note (do (store/dispatch! *:store [:resources/ensure! resource])
                              (store/subscribe *:store [:resources/?:resource resource]))]
      [comp/with-resources [sub:note] [root* *:store]]
      (finally
        (store/emit! *:store [:resources/destroyed resource])))))
