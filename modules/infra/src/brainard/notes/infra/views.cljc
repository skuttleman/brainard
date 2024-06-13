(ns brainard.notes.infra.views
  (:require
    [brainard.api.utils.fns :as fns]
    [brainard.infra.store.core :as store]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.controls.core :as ctrls]
    [clojure.string :as string]
    [defacto.forms.core :as forms]
    [whet.utils.navigation :as nav]
    [whet.utils.reagent :as r]))

(defn ^:private edit-link [note-id]
  [:a.link.space--left {:href (nav/path-for rte/all-routes :routes.ui/note {:notes/id note-id})}
   "edit"])

(defn ^:private tag-list [tags]
  [:div.flex
   [comp/tag-list {:value (take 8 tags)}]
   (when (< 8 (count tags))
     [:em.space--left "more..."])])

(defn ^:private note-item [{:keys [hide-context?]} {:notes/keys [id context body tags]} *:expanded]
  (let [expanded-id @*:expanded
        expanded? (= id expanded-id)]
    [:li.layout--stack-between {:id    id
                                :class [(when expanded? "expanded")]}
     [:div {:class    [(if expanded? "layout--stack-between" "layout--row")]
            :on-click (fn [_]
                        (reset! *:expanded (when-not expanded? id)))
            :style    {:cursor :pointer}}
      (when-not hide-context? [:strong.layout--no-shrink context])
      (if expanded?
        [comp/markdown body]
        [:span.flex-grow.space--left.truncate
         body])
      (when-not expanded?
        [edit-link id])]
     [tag-list tags]
     (when expanded?
       [edit-link id])]))

(defn ^:private history-change [k {:keys [added from removed to]}]
  [:div.layout--row
   [:span.purple (pr-str k)]
   (when (some? added)
     [:<>
      [:em.space--left "added"]
      [:span.space--left.truncate.blue
       (str added)]])
   (when (some? removed)
     [:<>
      [:em.space--left "removed"]
      [:span.space--left.truncate.orange
       (str removed)]])
   (cond
     (and (some? from) (some? to)) [:<>
                                    [:em.space--left "changed"]
                                    [:span.space--left.truncate.orange {:style {:max-width "50%"}}
                                     (str from)]
                                    [:em.space--left "to"]
                                    [:span.space--left.truncate.blue {:style {:max-width "50%"}}
                                     (str to)]]
     (some? from) [:<>
                   [:em.space--left "removed"]
                   [:span.space--left.truncate.orange
                    (str from)]]
     (some? to) [:<>
                 [:em.space--left "added"]
                 [:span.space--left.truncate.blue
                  (str to)]])])

(defmethod icomp/modal-body ::view
  [_ note]
  [:div.layout--stack-between
   [:h1.layout--space-after.flex-grow [:strong (:notes/context note)]]
   [comp/markdown (:notes/body note)]
   [tag-list (:notes/tags note)]])

(defn note-history [*:store entries reconstruction]
  [:ul.note-history
   (for [{:notes/keys [changes history-id saved-at]} entries]
     ^{:key history-id}
     [:li.layout--stack-between
      [:div.layout--row.layout--align-center
       [:span.layout--space-after.purple "saved at"]
       [:span.yellow (pr-str saved-at)]
       #_[comp/plain-button {:class ["is-small" "is-info"]
                           :on-click (fn [_]
                                       (store/dispatch! *:store [:modals/create! [::view (get reconstruction history-id)]]))}
        "view"]]
      (into [:<>]
            (for [k [:notes/context :notes/pinned? :notes/body :notes/tags]
                  :let [change (k changes)]
                  :when change]
              [history-change k change]))])])

(defn ^:private with-trim-on-blur [{:keys [on-change] :as attrs} *:store]
  (update attrs :on-blur fns/safe-comp (fn [e]
                                         (let [v (dom/target-value e)
                                               trimmed-v (not-empty (string/trim v))]
                                           (when (not= trimmed-v v)
                                             (store/emit! *:store (conj on-change trimmed-v)))
                                           e))))

(defn ^:private topic-field [{:keys [*:store form+ on-context-blur sub:contexts]}]
  [:div.layout--space-between
   [:div.flex-grow
    [ctrls/type-ahead (-> {:*:store     *:store
                           :label       "Topic"
                           :sub:items   sub:contexts
                           :auto-focus? true
                           :on-blur     on-context-blur}
                          (ctrls/with-attrs form+ [:notes/context])
                          (with-trim-on-blur *:store))]]
   [ctrls/icon-toggle (-> {:*:store *:store
                           :label   "Pinned"
                           :icon    :paperclip}
                          (ctrls/with-attrs form+ [:notes/pinned?]))]])

(defn ^:private body-field [{:keys [*:store form+]}]
  (let [form-data (forms/data form+)]
    [:<>
     [:label.label "Body"]
     [:div {:style {:margin-top 0}}
      (if (::preview? form-data)
        [:div.expanded
         [comp/markdown (:notes/body form-data)]]
        [ctrls/textarea (-> {:style   {:font-family :monospace
                                       :min-height  "250px"}
                             :*:store *:store}
                            (ctrls/with-attrs form+ [:notes/body]))])]]))

(defn note-list [attrs notes]
  (r/with-let [*:expanded (r/atom nil)]
    (if-not (seq notes)
      [:span.search-results
       [comp/alert :info "No search results"]]
      [:ul.search-results
       (for [{:notes/keys [id] :as note} notes]
         ^{:key id}
         [note-item attrs note *:expanded])])))

(defn note-form [{:keys [*:store form+ sub:tags] :as attrs}]
  [ctrls/form attrs
   [:strong "Create a note"]
   [topic-field attrs]
   [body-field attrs]
   [ctrls/toggle (-> {:label   [:span.is-small "Preview"]
                      :style   {:margin-top 0}
                      :inline? true
                      :*:store *:store}
                     (ctrls/with-attrs form+ [::preview?]))]
   [ctrls/tags-editor (-> {:*:store   *:store
                           :form-id   [::tags (forms/id form+)]
                           :label     "Tags"
                           :sub:items sub:tags}
                          (ctrls/with-attrs form+ [:notes/tags]))]])
