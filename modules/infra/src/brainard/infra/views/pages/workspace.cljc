(ns brainard.infra.views.pages.workspace
  (:require
    [brainard.infra.views.components.drag-drop :as drag]
    [brainard.infra.views.pages.interfaces :as ipages]
    [whet.utils.reagent :as r]))

(defonce tree
  (r/atom [{:id       :foo
            :content  "foo"
            :children [{:id        :baz
                        :parent-id :foo
                        :content   "baz"
                        :children  [{:id        :quux
                                     :parent-id :baz
                                     :content   "quux"
                                     :children  [{:id        :filth
                                                  :parent-id :quux
                                                  :content   "filth"
                                                  :children  []}]}]}
                       {:id        :poo
                        :parent-id :foo
                        :content   "poo"
                        :children  []}]}
           {:id       :bar
            :content  "bar"
            :children []}]))

(defmethod ipages/page :routes.ui/workspace
  [*:store _]
  [:div
   [:h1.subtitle "The page"]
   [drag/control {:id ::workspace :*:store *:store :on-drop println} @tree]])
