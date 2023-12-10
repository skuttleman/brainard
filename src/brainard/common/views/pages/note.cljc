(ns brainard.common.views.pages.note
  "The page for viewing a note and editing its tags."
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.resources.specs :as-alias rspecs]
    [brainard.common.store.core :as store]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.utils.maps :as maps]
    [brainard.common.utils.uuids :as uuids]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]
    [clojure.pprint :as pp]
    [clojure.set :as set]))

(def ^:private ^:const form-id
  ::forms/edit-note)

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
                    [:em.blue (pp/cl-format nil "~:R" v)]
                    [:span "day of the month"]]

    :schedules/week-index [:<>
                           [:span "during the"]
                           [:em.blue (pp/cl-format nil "~:R" (inc v))]
                           [:span "week of the month"]]
    nil))

(defn ^:private schedule-display [form-data]
  (when-let [parts (seq (->> form-data
                             (sort-by key)
                             reverse
                             (keep ->schedule-part)
                             (interpose [:span "AND"])))]
    (into [:div.flex.layout--room-between] parts)))

(defn ^:private schedules-list [note]
  [:div
   (if-let [scheds (seq (:notes/schedules note))]
     [:<>
      [:p [:em "Existing schedules"]]
      [:ul.layout--stack-between
       (for [sched scheds]
         ^{:key (:schedules/id sched)}
         [:li.layout--room-between.layout--align-center.space--left
          [comp/plain-button {:class    ["is-danger" "is-light" "is-small"]
                              :on-click (fn [_]
                                          ;; TODO impl
                                          (println "DELETE" (:schedules/id sched)))}
           [comp/icon :trash]]
          [schedule-display sched]])]]
     [:p [:em "no related schedules"]])])

(defn ^:private schedules-form [{:keys [*:store sub:form sub:res]} note]
  (let [form @sub:form
        form-id (forms/id form)
        form-data (forms/data form)]
    [ctrls/form {:*:store      *:store
                 :horizontal?  true
                 :form         form
                 :params       {:reset-to {:schedules/note-id (:notes/id note)}
                                :data     form-data}
                 :resource-key [::rspecs/schedules#create form-id]
                 :sub:res      sub:res
                 :submit/body  "Save"}
     [ctrls/select (-> {:*:store *:store
                        :label   "Day of the month"}
                       (ctrls/with-attrs form sub:res [:schedules/day]))
      day-options]
     [ctrls/select (-> {:*:store *:store
                        :label   "Day of the week"}
                       (ctrls/with-attrs form sub:res [:schedules/weekday]))
      weekday-options]
     [ctrls/select (-> {:*:store *:store
                        :label   "Week of the month"}
                       (ctrls/with-attrs form sub:res [:schedules/week-index]))
      week-index-options]
     [ctrls/select (-> {:*:store *:store
                        :label   "Month of the year"}
                       (ctrls/with-attrs form sub:res [:schedules/month]))
      month-options]]))

(defn ^:private schedules-editor [{:keys [*:store] :as attrs} note]
  (r/with-let [form-id (uuids/random)
               sub:form (do (store/dispatch! *:store [:forms/ensure!
                                                      form-id
                                                      {:schedules/note-id (:notes/id note)}
                                                      {:remove-nil? true}])
                            (store/subscribe *:store [:forms/?:form form-id]))
               sub:res (store/subscribe *:store [:resources/?:resource [::rspecs/schedules#create form-id]])]
    [:div.layout--stack-between
     [:div.flex.row
      [:em "Add a schedule: "]
      [:span.space--left [schedule-display (forms/data @sub:form)]]]
     [schedules-form (merge attrs (maps/m sub:form sub:res)) note]
     [schedules-list note]]))

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
