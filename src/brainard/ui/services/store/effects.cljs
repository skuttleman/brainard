(ns brainard.ui.services.store.effects
  (:require
    [brainard.ui.services.store.api :as store.api]))

(defn fetch-tags [_ _]
  {::store.api/request {:route        :routes.api/tags
                        :method       :get
                        :on-success-n [[:resources/succeeded :api.tags/fetch]]
                        :on-error-n   [[:resources/failed :api.tags/fetch]]}
   :dispatch           [:resources/submit! :api.tags/fetch]})

(defn fetch-contexts [_ _]
  {::store.api/request {:route        :routes.api/contexts
                        :method       :get
                        :on-success-n [[:resources/succeeded :api.contexts/fetch]]
                        :on-error-n   [[:resources/failed :api.contexts/fetch]]}
   :dispatch           [:resources/submit! :api.contexts/fetch]})

(defn search-notes [_ [_ resource-id params]]
  {::store.api/request {:route        :routes.api/notes
                        :method       :get
                        :query-params params
                        :on-success-n [[:resources/succeeded [:api.notes/search resource-id]]]
                        :on-error-n   [[:resources/failed [:api.notes/search resource-id]]]}
   :dispatch           [:resources/submit! [:api.notes/search resource-id]]})

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
                                       [:resources/failed [:api.notes/create! resource-id]]]}
   :dispatch           [:resources/submit! [:api.notes/create! resource-id]]})
