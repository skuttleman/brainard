(ns brainard.ui.store.effects
  (:require
    [brainard.ui.store.api :as store.api]))

(defn fetch-tags [{:keys [db]} _]
  {::store.api/request {:route      :routes.api/tags
                        :method     :get
                        :on-success [::store.api/success :tags]
                        :on-error   [::store.api/error :tags]}
   :db                 (assoc db :tags [:requesting])})

(defn fetch-contexts [{:keys [db]} _]
  {::store.api/request {:route      :routes.api/contexts
                        :method     :get
                        :on-success [::store.api/success :contexts]
                        :on-error   [::store.api/error :contexts]}
   :db                 (assoc db :contexts [:requesting])})

(defn create-note! [_ [_ note]]
  {::store.api/request {:route  :routes.api/notes
                        :method :post
                        :body   note}})

(defn update-note! [_ [_ note-id note]]
  {::store.api/request {:route        :routes.api/note
                        :route-params {:notes/id note-id}
                        :method       :patch
                        :body         note}})
