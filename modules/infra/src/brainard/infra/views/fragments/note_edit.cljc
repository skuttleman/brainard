(ns brainard.infra.views.fragments.note-edit
  (:require
    [brainard.api.utils.fns :as fns]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.stubs.dom :as dom]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.interfaces :as icomp]
    [brainard.infra.views.controls.core :as ctrls]
    [clojure.string :as string]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]))

(defn ^:private with-trim-on-blur [{:keys [on-change] :as attrs} *:store]
  (update attrs :on-blur fns/safe-comp (fn [e]
                                         (let [v (dom/target-value e)
                                               trimmed-v (not-empty (string/trim v))]
                                           (when (not= trimmed-v v)
                                             (store/emit! *:store (conj on-change trimmed-v)))
                                           e))))

(defn ^:private topic+pin-field [{:keys [*:store form+ on-context-blur sub:contexts]}]
  (let [form-data (forms/data form+)]
    [:div.layout--space-between
     [:div.flex-grow
      [ctrls/type-ahead (-> {:*:store     *:store
                             :label       "Topic"
                             :sub:items   sub:contexts
                             :on-blur     on-context-blur
                             :auto-focus? (nil? (:notes/context form-data))}
                            (ctrls/with-attrs form+ [:notes/context])
                            (with-trim-on-blur *:store))]]
     [ctrls/icon-toggle (-> {:*:store *:store
                             :label   "Pin"
                             :icon    :paperclip}
                            (ctrls/with-attrs form+ [:notes/pinned?]))]]))

(defn ^:private body-field [{:keys [*:store form+]}]
  (let [form-data (forms/data form+)]
    [:<>
     [:label.label "Body"]
     [:div {:style {:margin-top 0}}
      (if (::preview? form-data)
        [:div.expanded
         [comp/markdown (:notes/body form-data)]]
        [ctrls/textarea (-> {:style       {:font-family :monospace
                                           :min-height  "250px"}
                             :*:store     *:store
                             :auto-focus? (some? (:notes/context form-data))}
                            (ctrls/with-attrs form+ [:notes/body]))])]]))

(defn ^:private tags+attachments-field [{:keys [*:store form+ sub:tags uploading?]}]
  [:div.layout--room-between
   [:div {:style {:flex-basis "50%"}}
    [ctrls/tags-editor (-> {:*:store   *:store
                            :form-id   [::tags (forms/id form+)]
                            :label     "Tags"
                            :sub:items sub:tags}
                           (ctrls/with-attrs form+ [:notes/tags]))]]
   [:div {:style {:flex-basis "50%"}}
    [ctrls/file {:on-upload (fn [files]
                              (when (seq files)
                                (store/dispatch! *:store
                                                 [::res/submit!
                                                  [::specs/attachment#upload]
                                                  {:files     files
                                                   :ok-events [[::forms/modified
                                                                (forms/id form+)
                                                                [:notes/attachments]
                                                                into]]}])))
                 :disabled  uploading?
                 :label     "Attachments"
                 :multi?    true}]]])

(defn ^:private note-form [{:keys [*:store form+] :as attrs}]
  (r/with-let [sub:uploads (store/subscribe *:store [::res/?:resource [::specs/attachment#upload]])]
    (let [uploading? (res/requesting? @sub:uploads)]
      [ctrls/form (assoc attrs :disabled uploading?)
       [topic+pin-field attrs]
       [body-field attrs]
       [ctrls/toggle (-> {:label   [:span.is-small "Preview"]
                          :style   {:margin-top 0}
                          :inline? true
                          :*:store *:store}
                         (ctrls/with-attrs form+ [::preview?]))]
       [tags+attachments-field (assoc attrs :uploading? uploading?)]])
    (finally
      (store/emit! *:store [::res/destroyed [::specs/attachment#upload]]))))

(defmethod icomp/modal-header ::modal
  [_ {:keys [header]}]
  header)

(defmethod icomp/modal-body ::modal
  [*:store {modal-id :modals/id :modals/keys [close!] :keys [init params resource-key]}]
  (r/with-let [sub:form+ (store/form+-sub *:store resource-key init)
               sub:contexts (store/res-sub *:store [::specs/contexts#select])
               sub:tags (store/res-sub *:store [::specs/tags#select])
               params (update params :ok-commands conj [:modals/remove! modal-id])]
    [:div {:style {:min-width "50vw"}}
     [note-form {:*:store      *:store
                 :form+        @sub:form+
                 :params       params
                 :resource-key resource-key
                 :sub:contexts sub:contexts
                 :sub:tags     sub:tags
                 :submit/body  "Save"
                 :buttons      [[comp/plain-button {:on-click close!}
                                 "Cancel"]]}]]
    (finally
      (store/emit! *:store [::forms+/destroyed resource-key]))))
