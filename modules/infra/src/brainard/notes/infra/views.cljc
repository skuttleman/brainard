(ns brainard.notes.infra.views
  "Reagent components that render notes."
  (:require
   [brainard.infra.store.specs :as-alias specs]
   [brainard.infra.stubs.dom :as dom]
   [brainard.infra.views.components.core :as comp]
   [defacto.resources.core :as-alias res]
   [whet.utils.reagent :as r]))

(defn ^:private edit-link [note-id]
  [comp/link {:class        ["space--left" "note__edit-link"]
              :token        :routes.ui/note
              :route-params {:notes/id note-id}}
   "edit"])

(defn ^:private restore-button [*:store note-id]
  [comp/plain-button {:*:store  *:store
                      :class    ["is-ghost" "space--left" "note__restore-button"]
                      :style    {:justify-content :flex-start
                                 :padding         0}
                      :on-click dom/stop-propagation!
                      :commands [[::res/submit!
                                  [::specs/notes#modify note-id]
                                  {:payload      {:notes/archived? false}
                                   :ok-commands  [[:nav/navigate! {:token        :routes.ui/note
                                                                   :route-params {:notes/id note-id}}]]
                                   :err-commands [[:toasts/fail! {:message "failed to restore note"}]]}]]}
   "restore"])

(defn ^:private tag-list [tags]
  [:div.flex
   [comp/tag-list {:value (take 8 tags)}]
   (when (< 8 (count tags))
     [:em.space--left "more..."])])

(defn ^:private note-item [{:keys [*:store anchor anchor? hide-context?]} note *:expanded]
  (let [{:notes/keys [id archived? context body tags]} note
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
      (when (or archived? (not hide-context?))
        [:div.layout--row.layout--room-between
         (when-not hide-context? [:strong.layout--no-shrink context])
         (when archived? [:em.has-text-danger "[archived]"])])
      (if expanded?
        [comp/markdown body]
        [:span.flex-grow.space--left.truncate
         body])
      (when-not expanded?
        (if archived?
          (when *:store
            [restore-button *:store id])
          [edit-link id]))]
     [tag-list tags]
     (when expanded?
       (if archived?
         (when *:store
           [restore-button *:store id])
         [edit-link id]))]))

(defn note-list [attrs notes]
  (r/with-let [*:expanded (r/atom nil)]
    (if-not (seq notes)
      [:span.search-results
       [comp/alert :info "No search results"]]
      [:ul.search-results
       (for [{:notes/keys [id] :as note} notes]
         ^{:key id}
         [note-item attrs note *:expanded])])))
