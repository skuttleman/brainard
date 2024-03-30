(ns brainard.infra.views.pages.workspace
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.controls.core :as ctrls]
    [brainard.infra.views.pages.interfaces :as ipages]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]
    [whet.utils.reagent :as r]))

(declare tree-list)

(def ^:private ^:const tree-form-id
  [::forms+/std [::specs/local ::specs/workspace#modify!]])

(defn ^:private ->new-form-id [node-id]
  [::forms+/std [::specs/local ::specs/workspace#create! node-id]])

(defn ^:private ->edit-form-id [node-id]
  [::forms+/std [::specs/local ::specs/workspace#update! node-id]])

(defn ^:private ->on-submit [*:store close-form form-id]
  (fn [_]
    (store/dispatch! *:store [::forms+/submit! form-id])
    (close-form)))

(defn ^:private node-form [*:store close-form form-id data clean?]
  (r/with-let [sub:form+ (do (if clean?
                               (store/emit! *:store [::forms/created form-id data])
                               (store/dispatch! *:store [::forms/ensure! form-id data]))
                             (store/subscribe *:store [::forms+/?:form+ form-id]))
               on-submit (->on-submit *:store close-form form-id)]
    (let [form+ @sub:form+]
      [ctrls/plain-form {:form+           form+
                         :inline-buttons? true
                         :on-submit       on-submit}
       [ctrls/input (-> {:*:store     *:store
                         :auto-focus? true}
                        (ctrls/with-attrs form+ [:workspace-nodes/data]))]])
    (finally
      (if clean?
        (store/emit! *:store [::forms+/destroyed form-id])
        (store/emit! *:store [::forms+/recreated form-id])))))

(defn ^:private new-node-toggle [*:store node-id]
  (r/with-let [*:open? (r/atom false)
               on-change (partial reset! *:open?)]
    (let [open? @*:open?]
      [:div.flex.layout--room-between
       {:style (when-not open? {:margin-top    "-2px"
                                :margin-bottom "-16px"})}
       [comp/plain-toggle {:class     ["tab-item" "is-white" "is-small" "tab-item"]
                           :on-change on-change
                           :value     open?}]
       (when open?
         (let [form-id (->new-form-id node-id)
               init (when node-id {:workspace-nodes/parent-id node-id})]
           ^{:key (or node-id "new")}
           [node-form *:store on-change form-id init false]))])))

(defn ^:private tree-node [*:store sub:form+ {:workspace-nodes/keys [id nodes] :as node}]
  (r/with-let [edit-form-id (->edit-form-id id)
               delete-modal [:modals/sure?
                             {:description  "Delete this sub tree?"
                              :yes-commands [[::res/submit! [::specs/local ::specs/workspace#delete!] node]]}]
               on-edit-close (fn []
                               (store/emit! *:store [::forms/changed tree-form-id [:editing] nil]))]
    (let [{:keys [editing selected]} (forms/data @sub:form+)
          editing? (when editing
                     (= (:workspace-nodes/id editing) id))
          selected? (= selected node)]
      [:div (cond-> {:style {:margin-left "8px"}}
              (empty? nodes) (assoc :class ["flex" "row"]))
       (if editing?
         (let [init (select-keys editing #{:workspace-nodes/id :workspace-nodes/data})]
           [:div {:on-key-down (fn [e]
                                 (when (= :key-codes/esc (dom/event->key e))
                                   (on-edit-close)))}
            ^{:key id}
            [node-form *:store on-edit-close edit-form-id init true]])
         [:div.flex.row
          [comp/plain-input {:class    ["button" "tab-item"
                                        (if selected? "is-outlined" "is-white")]
                             :style    {:width :unset}
                             :type     :button
                             :on-click (fn [_]
                                         (if selected?
                                           (store/emit! *:store [::forms/changed tree-form-id [:selected] nil])
                                           (store/emit! *:store [::forms/changed tree-form-id [:selected] node])))
                             :value    (:workspace-nodes/data node)}]
          [comp/plain-button {:class    ["is-white" "is-small" "tab-item"]
                              :on-click (fn [_]
                                          (store/emit! *:store [::forms/changed tree-form-id [:editing] node]))}
           [comp/icon :pencil]]
          [comp/plain-button {:class    ["is-white" "is-small" "tab-item"]
                              :on-click (fn [_]
                                          (store/dispatch! *:store [:modals/create! delete-modal]))}
           [comp/icon {:class ["is-danger"]} :trash-can]]])
       (when (seq nodes)
         [tree-list *:store sub:form+ nodes id])])))

(defn ^:private tree-list [*:store sub:form+ nodes node-id]
  [:ul.tree-list.layout--stack-between
   {:class [(when (seq nodes) "bullets")]}
   (doall (for [node (sort-by :workspace-nodes/index nodes)]
            ^{:key (:workspace-nodes/id node)}
            [:li [tree-node *:store sub:form+ node]]))
   ^{:key (or node-id "new")}
   [:li [new-node-toggle *:store node-id]]])

(defn ^:private root [*:store sub:form+ [root-nodes]]
  [:div.tree-root {:on-key-up   (fn [e]
                                  (when-let [code (#{:key-codes/up :key-codes/down :key-codes/left :key-codes/right}
                                                   (dom/event->key e))]
                                    (when-let [node (:selected (forms/data @sub:form+))]
                                      (let [op (case code
                                                 :key-codes/up ::specs/workspace#up!
                                                 :key-codes/down ::specs/workspace#down!
                                                 :key-codes/left ::specs/workspace#unnest!
                                                 :key-codes/right ::specs/workspace#nest!)]
                                        (store/dispatch! *:store [::res/submit! [::specs/local op] (assoc node :forms/id tree-form-id)])))))
                   :on-key-down (fn [e]
                                  (when (and (#{:key-codes/up :key-codes/down :key-codes/left :key-codes/right}
                                              (dom/event->key e))
                                             (:selected (forms/data @sub:form+)))
                                    (dom/prevent-default! e)))}
   [:h2.subtitle "Welcome to your workspace"]
   [tree-list *:store sub:form+ root-nodes nil]])

(defmethod ipages/page :routes.ui/workspace
  [*:store _]
  (r/with-let [sub:data (do #?(:cljs (store/dispatch! *:store [::res/submit! [::specs/local ::specs/workspace#fetch]]))
                            (store/subscribe *:store [::res/?:resource [::specs/local ::specs/workspace#fetch]]))
               sub:form+ (do (store/dispatch! *:store [::forms/ensure! tree-form-id])
                             (store/subscribe *:store [::forms+/?:form+ tree-form-id]))]
    [comp/with-resources [sub:data] [root *:store sub:form+]]
    (finally
      (store/emit! *:store [::res/destroyed [::specs/local ::specs/workspace#fetch]]))))
