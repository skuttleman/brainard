(ns brainard.notes.infra.views
  (:require
    [brainard.api.utils.dates :as dates]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.interfaces :as icomp]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(defn ^:private edit-link [note-id]
  [comp/link {:class        ["space--left"]
              :token        :routes.ui/note
              :route-params {:notes/id note-id}}
   "edit"])

(defn ^:private tag-list [tags]
  [:div.flex
   [comp/tag-list {:value (take 8 tags)}]
   (when (< 8 (count tags))
     [:em.space--left "more..."])])

(defn ^:private note-item [{:keys [anchor anchor? hide-context?]} note *:expanded]
  (let [{:notes/keys [id context body tags]} note
        expanded-id @*:expanded
        expanded? (= id expanded-id)]
    [:li.layout--stack-between {:id    id
                                :class [(when expanded? "expanded")
                                        (when (= (str id) anchor) "anchored")]}
     [:div {:class    [(if expanded? "layout--stack-between" "layout--row")]
            :on-click (fn [_]
                        (reset! *:expanded (when-not expanded? id)))
            :style    {:cursor :pointer}}
      (when (and anchor? (not expanded?))
        [comp/plain-button {:class    ["is-small" "is-ghost"]
                            :on-click (fn [e]
                                        (dom/stop-propagation! e)
                                        #?(:cljs (let [link (str js/location.origin
                                                                 js/location.pathname
                                                                 js/location.search
                                                                 "#"
                                                                 id)]
                                                   (.writeText js/navigator.clipboard link))))}
         [comp/icon :link]])
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

(defn ^:private history-change [label {:keys [added from removed to]}]
  [:div.layout--row
   [:span.purple label]
   (when (some? added)
     [:<>
      [:em.space--left "added"]
      [:span.space--left.truncate.blue
       (apply str (interpose ", " added))]])
   (when (some? removed)
     [:<>
      [:em.space--left "removed"]
      [:span.space--left.truncate.orange
       (apply str (interpose ", " removed))]])
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

(defmethod icomp/modal-header ::view
  [_ {:notes/keys [saved-at]}]
  (dates/->str saved-at))

(defmethod icomp/modal-body ::view
  [*:store {modal-id :modals/id :keys [last? note] :as params}]
  [:div.layout--stack-between
   [:div.layout--row
    (when (:notes/pinned? note)
      [comp/icon {:class ["layout--space-after"]
                  :style {:align-self :center}} :paperclip])
    [:h1 [:strong (:notes/context note)]]]
   [comp/markdown (:notes/body note)]
   [tag-list (:notes/tags note)]
   (when-not last?
     [:div
      [comp/plain-button {:*:store  *:store
                          :class    ["is-small" "is-info"]
                          :commands [[:modals/remove! modal-id]
                                     [::res/submit! [::specs/notes#reinstate modal-id] params]]}
       "reinstate"]])])

(defn note-history [*:store reconstruction entries]
  (let [entry-count (count entries)
        prev-tags (:notes/tags (get reconstruction (:notes/history-id (last entries))))]
    [:ul.note-history
     (for [[idx {:notes/keys [changes history-id saved-at]}] (map-indexed vector entries)
           :let [history-modal [::view {:last?     (= idx (dec entry-count))
                                        :note      (get reconstruction history-id)
                                        :prev-tags prev-tags}]]]
       ^{:key history-id}
       [:li.layout--stack-between
        [:div.layout--row.layout--align-center.layout--space-between
         [:div
          [:span.layout--space-after.green (dates/->str saved-at)]]
         [comp/plain-button {:*:store  *:store
                             :class    ["is-small" "is-info"]
                             :commands [[:modals/create! history-modal]]}
          "view"]]
        (into [:<>]
              (for [[k label] [[:notes/context "Context"]
                               [:notes/pinned? "Pin"]
                               [:notes/body "Body"]
                               [:notes/tags "Tags"]]
                    :let [change (k changes)]
                    :when change]
                [history-change label change]))])]))

(defn note-list [attrs notes]
  (r/with-let [*:expanded (r/atom nil)]
    (if-not (seq notes)
      [:span.search-results
       [comp/alert :info "No search results"]]
      [:ul.search-results
       (for [{:notes/keys [id] :as note} notes]
         ^{:key id}
         [note-item attrs note *:expanded])])))
