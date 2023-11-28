(ns brainard.ui.services.store.core
  (:require
    [brainard.ui.services.store.effects :as store.effects]
    [brainard.ui.services.store.events :as store.events]
    [brainard.ui.services.store.subscriptions :as store.subs]
    [brainard.ui.services.store.toasts :as store.toasts]
    [re-frame.core :as rf*]))

(def ^:private empty-store
  {:routing/route nil})

;; CORE
(rf*/reg-event-db :core/init (constantly empty-store))
(rf*/reg-cofx :core/now (fn [cofx _] (assoc cofx :core/now (js/Date.))))


;; ROUTING
(rf*/reg-sub :routing/route (store.subs/get-path [:routing/route]))
(rf*/reg-event-db :routing/navigate (store.events/assoc-path [:routing/route]))


;; RESOURCE
(rf*/reg-sub :resources/resource store.subs/resource)
(rf*/reg-event-db :resources/submit! store.events/submit-resource)
(rf*/reg-event-db :resources/succeeded store.events/resource-succeeded)
(rf*/reg-event-db :resources/failed store.events/resource-failed)
(rf*/reg-event-db :resources/destroy store.events/destroy-resource)
(rf*/reg-event-db :resources.tags/include-note store.events/add-tags)
(rf*/reg-event-db :resources.contexts/include-note store.events/add-context)

;; API
(rf*/reg-event-fx :api.tags/fetch store.effects/fetch-tags)
(rf*/reg-event-fx :api.contexts/fetch store.effects/fetch-contexts)
(rf*/reg-event-fx :api.notes/search store.effects/search-notes)
(rf*/reg-event-fx :api.notes/create! store.effects/create-note!)


;; FORM
(rf*/reg-sub :forms/form store.subs/form)
(rf*/reg-event-db :forms/create store.events/create-form)
(rf*/reg-event-db :forms/destroy store.events/destroy-form)
(rf*/reg-event-db :forms/change store.events/change-form)


;; TOASTS
(rf*/reg-sub :toasts/toasts store.subs/toasts)
(rf*/reg-event-fx :toasts/success store.toasts/on-success)
(rf*/reg-event-fx :toasts/failure store.toasts/on-failure)
(rf*/reg-event-fx :toasts/create
                  [(rf*/inject-cofx :core/now)]
                  store.toasts/create)
(rf*/reg-event-db :toasts/show store.toasts/show)
(rf*/reg-event-fx :toasts/hide store.toasts/hide)
(rf*/reg-event-db :toasts/destroy store.toasts/destroy)
