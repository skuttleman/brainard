(ns brainard.notes.infra.views
  (:require
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.components.core :as comp]
    [whet.utils.navigation :as nav]
    [whet.utils.reagent :as r]))

(defn note-list [{:keys [hide-context?]} notes]
  (r/with-let [sub:expanded (r/atom nil)]
    (let [expanded-id @sub:expanded]
      (if-not (seq notes)
        [:span.search-results
         [comp/alert :info "No search results"]]
        [:ul.search-results
         (for [{:notes/keys [id context body tags]} notes
               :let [expanded? (= id expanded-id)]]
           ^{:key id}
           [:li.layout--stack-between {:id    id
                                       :class [(when expanded? "expanded")]}
            [:div {:class    [(if expanded? "layout--stack-between" "layout--row")]
                   :on-click (fn [_]
                               (reset! sub:expanded (when-not expanded? id)))
                   :style    {:cursor :pointer}}
             (when-not hide-context? [:strong.layout--no-shrink context])
             (if expanded?
               [comp/markdown body]
               [:span.flex-grow.space--left.truncate
                body])
             (when-not expanded?
               [:a.link.space--left {:href (nav/path-for rte/all-routes :routes.ui/note {:notes/id id})}
                "edit"])]
            [:div.flex
             [comp/tag-list {:value (take 8 tags)}]
             (when (< 8 (count tags))
               [:em.space--left "more..."])]
            (when expanded?
              [:a.link.space--left {:href (nav/path-for rte/all-routes :routes.ui/note {:notes/id id})}
               "edit"])])]))))
