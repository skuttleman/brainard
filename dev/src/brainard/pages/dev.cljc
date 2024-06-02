(ns brainard.pages.dev
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [defacto.core :as defacto]
    [defacto.forms.core :as forms]
    [whet.utils.reagent :as r]))

(defmethod defacto/query-responder ::?:events
  [db _]
  (::-events db))

(defn ^:private display-event [*:store form event]
  (let [{:keys [id occurred-at]} (meta event)
        form-data (forms/data form)
        expanded? (= id (::selected form-data))]
    [:div {:style    {:cursor :pointer}
           :on-click (fn [_]
                       (store/emit! *:store
                                    ^:skip-tracking?
                                    [::forms/changed
                                     [::event-viewer]
                                     [::selected]
                                     (when-not expanded? id)]))}
     (if expanded?
       [:div.layout--row
        [:div.flex-grow
         [comp/pprint event]]
        [comp/pprint occurred-at]]
       [comp/pprint (first event)])]))

(defn ^:private event-viewer [*:store events]
  (r/with-let [event-opts (->> (dissoc (methods defacto/event-reducer) :default)
                               keys
                               sort
                               (map (juxt identity str)))
               event-opts-by-id (into {} event-opts)
               sub:filter-form (-> *:store
                                   (store/emit! [::forms/created
                                                 [::event-viewer]
                                                 {:events (into #{} (map first) events)}])
                                   (store/subscribe [::forms/?:form [::event-viewer]]))]
    (let [form @sub:filter-form]
      [:div
       [:h2.subtitle "Event Navigator"]
       [ctrls/multi-dropdown (-> {:*:store       *:store
                                  :inline?       true
                                  :label         "Filter by event-type"
                                  :label-style   {:margin-bottom "16px"}
                                  :options       event-opts
                                  :options-by-id event-opts-by-id}
                                 (ctrls/with-attrs form [:events])
                                 (update :on-change vary-meta assoc :skip-tracking? true))]
       [:ul.defacto-events
        (for [event (cond->> events
                      (seq (:events (forms/data form)))
                      (filter (comp (:events (forms/data form)) first)))
              :let [{:keys [id]} (meta event)]]
          ^{:key id}
          [:li
           [display-event *:store form event]])]])))

(defmethod ipages/page :routes.ui/dev
  [*:store _route-info]
  (r/with-let [sub:events (store/subscribe *:store [::?:events])]
    [:div
     [:h1.title "Dev Station"]
     [event-viewer *:store @sub:events]]))
