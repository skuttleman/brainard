(ns brainard.ui.store.core
  (:require
    [brainard.ui.store.api :as api]
    [re-frame.core :as rf]))

(def ^{:arglists '([[type :as event]])} dispatch rf/dispatch)
(def ^{:arglists '([[type :as event]])} dispatch-sync rf/dispatch-sync)
(def ^{:arglists '([[type :as query]])} subscribe rf/subscribe)

(rf/reg-sub ::tags (fn [db _] (:tags db)))
(rf/reg-sub ::contexts (fn [db _] (:contexts db)))

(rf/reg-event-db
  ::init
  (constantly {:tags     [:init]
               :contexts [:init]}))

(rf/reg-event-fx
  ::fetch-tags
  (fn [{:keys [db]} _]
    {::api/request {:route      :routes.api/tags
                    :method     :get
                    :on-success [::api/success :tags]
                    :on-error   [::api/error :tags]}
     :db           (assoc db :tags [:requesting])}))

(rf/reg-event-fx
  ::fetch-contexts
  (fn [{:keys [db]} _]
    {::api/request {:route      :routes.api/contexts
                    :method     :get
                    :on-success [::api/success :contexts]
                    :on-error   [::api/error :contexts]}
     :db           (assoc db :contexts [:requesting])}))

(rf/reg-event-fx
  ::create-note!
  (fn [_ [_ note]]
    {::api/request {:route  :routes.api/notes
                    :method :post
                    :body   note}}))

(rf/reg-event-fx
  ::update-note!
  (fn [_ [_ note-id note]]
    {::api/request {:route        :routes.api/note
                    :route-params {:notes/id note-id}
                    :method       :patch
                    :body         note}}))
