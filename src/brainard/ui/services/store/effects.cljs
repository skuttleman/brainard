(ns brainard.ui.services.store.effects
  (:require
    [brainard.common.specs :as specs]
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

(defn with-form-submission [co-fx {:keys [errors form-id reset-to validator] :as params}]
  (let [success-handler (cond-> [:forms/succeeded form-id]
                          (contains? params :reset-to) (conj reset-to))]
    (if errors
      (update co-fx :dispatch-n (fnil conj []) [:forms/invalid form-id validator errors])
      (-> co-fx
          (update-in [::store.api/request :on-success-n]
                     (fnil conj [])
                     success-handler)
          (update-in [::store.api/request :on-error-n]
                     (fnil conj [])
                     [:forms/failed form-id])
          (update :dispatch-n (fnil conj []) [:forms/submit form-id validator])))))

(def ^:private new-note-validator
  (specs/->validator specs/new-note))

(def ^:private update-note-validator
  (specs/->validator specs/update-note))

(defn create-note! [_ [_ {:keys [data form-id] :as params}]]
  (let [errors (new-note-validator data)]
    (cond-> {}
      (nil? errors) (assoc ::store.api/request
                           {:route  :routes.api/notes
                            :method :post
                            :body   data})
      form-id (with-form-submission (assoc params :errors errors :validator new-note-validator)))))

(defn update-note! [_ [_ note-id {:keys [data form-id] :as params}]]
  (let [errors (update-note-validator data)]
    (cond-> {}
      (nil? errors) (assoc ::store.api/request
                           {:route        :routes.api/note
                            :route-params {:notes/id note-id}
                            :method       :patch
                            :body         data})
      form-id (with-form-submission (assoc params :errors errors :validator update-note-validator)))))
