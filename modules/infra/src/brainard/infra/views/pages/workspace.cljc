(ns brainard.infra.views.pages.workspace
  (:require
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.drag-drop :as dnd]
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

(defmulti ^:private drag-item (fn [_ attrs _] (:type attrs)))

(defmethod drag-item :static
  [*:store _ node]
  [:div.layout--row
   [:span.layout--space-after
    [:span (:content node)]]
   [:span.layout--space-after
    [comp/icon :pencil]]
   [:span.layout--space-after
    [comp/icon :plus]]
   [comp/icon :trash-can]])

(defmethod drag-item :default
  [_ _ node]
  [:span (:content node)])

(defmethod ipages/page :routes.ui/workspace
  [*:store _]
  (r/with-let [attrs {:*:store *:store
                      :comp [drag-item *:store]
                      :id ::workspace
                      :on-drop println}]
    [:div
     [:h1.subtitle "Welcome to your workspace"]
     [dnd/control attrs @tree]]))
