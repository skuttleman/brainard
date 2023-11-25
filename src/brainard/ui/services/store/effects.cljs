(ns brainard.ui.services.store.effects
  (:require
    [brainard.ui.services.store.api :as store.api]))

(defn fetch-tags [{:keys [db]} _]
  {::store.api/request {:route        :routes.api/tags
                        :method       :get
                        :on-success-n [[::store.api/success [:tags]]]
                        :on-error-n   [[::store.api/error [:tags]]]}
   :db                 (assoc db :tags [:requesting])})

(defn fetch-contexts [{:keys [db]} _]
  {::store.api/request {:route        :routes.api/contexts
                        :method       :get
                        :on-success-n [[::store.api/success [:contexts]]]
                        :on-error-n   [[::store.api/error [:contexts]]]}
   :db                 (assoc db :contexts [:requesting])})

(defn with-form-submission [co-fx {:keys [form-id reset] :as params}]
  (let [success-handler (cond-> [:forms/succeeded form-id]
                          (contains? params :reset) (conj reset))]
    (-> co-fx
        (update-in [::store.api/request :on-success-n]
                   (fnil conj [])
                   success-handler)
        (update-in [::store.api/request :on-error-n]
                   (fnil conj [])
                   [:forms/failed form-id])
        (update :dispatch-n (fnil conj []) [:forms/submit form-id]))))

(defn create-note! [_ [_ {:keys [data form-id] :as params}]]
  (cond-> {::store.api/request {:route  :routes.api/notes
                                :method :post
                                :body   data}}
    form-id (with-form-submission params)))

(defn update-note! [_ [_ note-id {:keys [data form-id]}]]
  (cond-> {::store.api/request {:route        :routes.api/note
                                :route-params {:notes/id note-id}
                                :method       :patch
                                :body         data}}
    form-id (with-form-submission form-id)))
