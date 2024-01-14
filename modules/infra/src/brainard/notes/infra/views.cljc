(ns brainard.notes.infra.views
  (:require
    [brainard.infra.utils.routing :as rte]
    [brainard.infra.views.components.core :as comp]
    [whet.navigation :as nav]))

(defn search-results [{:keys [anchor]} notes]
  (if-not (seq notes)
    [:span.search-results
     [comp/alert :info "No search results"]]
    [:ul.search-results
     (for [{:notes/keys [id context body tags]} notes]
       ^{:key id}
       [:li {:id    id
             :class [(when (= anchor (str id)) "anchored")]}
        [:div.layout--row
         [:strong.layout--no-shrink context]
         [:span.flex-grow.space--left.truncate
          body]
         [:a.link.space--left {:href (nav/path-for rte/all-routes :routes.ui/note {:notes/id id})}
          "view"]]
        [:div.flex
         [comp/tag-list {:value (take 8 tags)}]
         (when (< 8 (count tags))
           [:em.space--left "more..."])]])]))
