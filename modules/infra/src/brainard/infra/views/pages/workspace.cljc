(ns brainard.infra.views.pages.workspace
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [defacto.forms.core :as-alias forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(declare tree-list)

(defn ^:private ->form-id [node-id]
  [::forms+/std [::specs/local ::specs/workspace#create! node-id]])

(defn ^:private new-node-form [*:store node-id]
  (r/with-let [form-id (->form-id node-id)
               sub:form+ (do (store/dispatch! *:store [::forms/ensure! form-id
                                                       (when node-id {:workspace-nodes/parent-id node-id})])
                             (store/subscribe *:store [::forms+/?:form+ form-id]))]
    (let [form+ @sub:form+]
      [ctrls/form {:*:store      *:store
                   :form+        form+
                   :params       {:ok-commands [[::res/submit! [::specs/local ::specs/workspace#fetch]]]}
                   :resource-key form-id}
       [ctrls/input (-> {:label   "Data"
                         :class   ["inline"]
                         :*:store *:store}
                        (ctrls/with-attrs form+ [:workspace-nodes/data]))]])
    (finally
      (store/emit! *:store [::forms/destroyed form-id]))))

(defn ^:private tree-node [*:store {:workspace-nodes/keys [id data nodes]}]
  [:div {:style {:margin-left "8px"}}
   [:p data]
   [tree-list *:store nodes]
   [new-node-form *:store id]])

(defn ^:private tree-list [*:store nodes]
  (when (seq nodes)
    [:ul.layout--stack-between
     (for [node (sort-by :workspace-nodes/id nodes)]
       ^{:key (:workspace-nodes/id node)}
       [:li [tree-node *:store node]])]))

(defn ^:private root [*:store [root-nodes]]
  [:div
   [:h2.subtitle "Welcome to your workspace"]
   [tree-list *:store root-nodes]
   [new-node-form *:store nil]])

(defmethod ipages/page :routes.ui/workspace
  [{:keys [*:store]}]
  (r/with-let [sub:data (do #?(:cljs (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#fetch]]))
                            (store/subscribe *:store [::res/?:resource [::specs/local ::specs/workspace#fetch]]))]
    [comp/with-resources [sub:data] [root *:store]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/local ::specs/workspace#fetch]]))))
