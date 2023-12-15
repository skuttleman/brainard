(ns brainard.common.views.pages.home
  "The home page with note creation form."
  (:require
    [defacto.forms.core :as forms]
    [brainard.common.store.core :as store]
    [brainard.common.resources.specs :as-alias rspecs]
    [brainard.common.stubs.dom :as dom]
    [brainard.common.stubs.reagent :as r]
    [brainard.common.views.components.core :as comp]
    [brainard.common.views.controls.core :as ctrls]
    [brainard.common.views.pages.interfaces :as ipages]
    [brainard.common.views.pages.shared :as spages]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as-alias res]))

(def ^:private ^:const form-id
  ::forms/new-note)

(def ^:private new-note
  {:notes/body    nil
   :notes/context nil
   :notes/tags    #{}})

(defn ^:private ->context-blur [store]
  (fn [e]
    (store/dispatch! store
                     [::res/sync!
                      [::rspecs/notes#select form-id]
                      {:data {:notes/context (dom/target-value e)}}])))

(defn ^:private root* [{:keys [*:store sub:form+ sub:contexts sub:tags]}]
  (let [form+ @sub:form+]
    [ctrls/form {:*:store      *:store
                 :changed?     (forms/changed? form+)
                 :params       {:pre-events [[::res/destroyed [::rspecs/notes#select form-id]]]}
                 :resource-key [::forms+/post [::rspecs/notes#create form-id]]
                 :sub:res      sub:form+}
     [:strong "Create a note"]
     [ctrls/type-ahead (-> {:*:store   *:store
                            :label     "Context"
                            :sub:items sub:contexts
                            :on-blur   (->context-blur *:store)}
                           (ctrls/with-attrs form+ [:notes/context]))]
     [ctrls/textarea (-> {:label   "Body"
                          :*:store *:store}
                         (ctrls/with-attrs form+ [:notes/body]))]
     [ctrls/tags-editor (-> {:*:store   *:store
                             :label     "Tags"
                             :sub:items sub:tags}
                            (ctrls/with-attrs form+ [:notes/tags]))]]))

(defn ^:private search-results [route-info [notes]]
  [:div
   (if (seq notes)
     [:<>
      [:h3.subtitle [:em "Some related notes..."]]
      [spages/search-results route-info notes]]
     [:em "Brand new context!"])])

(defmethod ipages/page :routes.ui/home
  [{:keys [*:store] :as route-info}]
  (r/with-let [_ (store/dispatch! *:store [::forms/ensure! [::forms+/post [::rspecs/notes#create form-id]] new-note])
               sub:form+ (store/subscribe *:store [::forms+/?:form+ [::forms+/post [::rspecs/notes#create form-id]]])
               sub:contexts (store/subscribe *:store [::res/?:resource [::rspecs/contexts#select]])
               sub:tags (store/subscribe *:store [::res/?:resource [::rspecs/tags#select]])
               sub:notes (store/subscribe *:store [::res/?:resource [::rspecs/notes#select form-id]])]
    [:div
     [root* {:*:store      *:store
             :sub:form+    sub:form+
             :sub:contexts sub:contexts
             :sub:tags     sub:tags}]
     [comp/with-resources [sub:notes] [search-results (assoc route-info :hide-init? true)]]]
    (finally
      (store/emit! *:store [::forms+/destroyed [::forms+/post [::rspecs/notes#create form-id]]])
      (store/emit! *:store [::res/destroyed [::rspecs/notes#select form-id]]))))
