(ns brainard.ui.services.store.effects
  (:require
    [brainard.common.navigation.core :as nav]
    [brainard.common.utils.colls :as colls]
    [brainard.ui.services.store.api :as store.api]
    [re-frame.core :as rf*]))

(rf*/reg-fx
  ::navigate!
  (fn [{:keys [handler route-params query-params]}]
    (nav/navigate! handler (assoc route-params :query-params query-params))))

(defn fetch-tags [_ _]
  {::store.api/request {:route        :routes.api/tags
                        :method       :get
                        :on-success-n [[:resources/succeeded :api.tags/select]]
                        :on-error-n   [[:resources/failed :api.tags/select :remote]]}})

(defn fetch-contexts [_ _]
  {::store.api/request {:route        :routes.api/contexts
                        :method       :get
                        :on-success-n [[:resources/succeeded :api.contexts/select]]
                        :on-error-n   [[:resources/failed :api.contexts/select :remote]]}})

(defn fetch-note [_ [_ note-id]]
  {::store.api/request {:route        :routes.api/note
                        :route-params {:notes/id note-id}
                        :method       :get
                        :on-success-n [[:resources/succeeded [:api.notes/find note-id]]]
                        :on-error-n   [[:resources/failed [:api.notes/find note-id] :remote]]}})

(defn search-notes [_ [_ resource-id params]]
  {::store.api/request {:route        :routes.api/notes
                        :method       :get
                        :query-params params
                        :on-success-n [[:resources/succeeded [:api.notes/select resource-id]]]
                        :on-error-n   [[:resources/failed [:api.notes/select resource-id] :remote]]}})

(defn create-note! [_ [_ resource-id {:keys [data reset-to]}]]
  {::store.api/request {:route        :routes.api/notes
                        :method       :post
                        :body         data
                        :on-success-n (cond-> [[:toasts/success {:message "note created"}]
                                               [:resources.tags/from-note]
                                               [:resources.contexts/from-note]]
                                        reset-to
                                        (conj [:forms/create resource-id reset-to]
                                              [:resources/destroy [:api.notes/create! resource-id]])

                                        (nil? reset-to)
                                        (conj [:resources/succeeded [:api.notes/create! resource-id]]))
                        :on-error-n   [[:toasts/failure]
                                       [:resources/failed [:api.notes/create! resource-id] :remote]]}})

(defn update-note! [_ [_ resource-id {:keys [note-id data fetch? reset-to]}]]
  {::store.api/request {:route        :routes.api/note
                        :route-params {:notes/id note-id}
                        :method       :patch
                        :body         data
                        :on-success-n (cond-> [[:toasts/success {:message "note updated"}]
                                               [:resources.tags/from-note]
                                               [:resources.contexts/from-note]]
                                        reset-to
                                        (conj [:forms/create resource-id reset-to]
                                              [:resources/destroy [:api.notes/update! resource-id]])

                                        (nil? reset-to)
                                        (conj [:resources/succeeded [:api.notes/update! resource-id]])

                                        fetch?
                                        (conj [:resources/submit! [:api.notes/find note-id]]))
                        :on-error-n   [[:toasts/failure]
                                       [:resources/failed [:api.notes/update! resource-id] :remote]]}})

(defn submit-resource [{:keys [db]} [_ resource-id params]]
  (let [resource (colls/wrap-vector resource-id)
        mixins (meta resource)]
    (cond-> {:dispatch (cond-> resource params (conj params))
             :db       (assoc-in db [:resources/resources resource-id] [:requesting])}
      (:with-qp-sync? mixins)
      (assoc ::navigate! (assoc (:routing/route db) :query-params params)))))
