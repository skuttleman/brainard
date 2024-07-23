(ns brainard.notes.infra.views
  (:require
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
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

(defn note-list [attrs notes]
  (r/with-let [*:expanded (r/atom nil)]
    (if-not (seq notes)
      [:span.search-results
       [comp/alert :info "No search results"]]
      [:ul.search-results
       (for [{:notes/keys [id] :as note} notes]
         ^{:key id}
         [note-item attrs note *:expanded])])))
