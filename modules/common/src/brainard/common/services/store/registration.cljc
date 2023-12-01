(ns brainard.common.services.store.registration
  (:require
    [brainard.common.services.store.effects :as store.effects]
    [brainard.common.services.store.events :as store.events]
    [brainard.common.services.store.subscriptions :as store.subs]
    [brainard.common.services.store.toasts :as store.toasts]
    [re-frame.core :as rf]))

(def ^:private empty-store
  {:routing/route nil})

;; CORE
(rf/reg-event-db :core/init (constantly empty-store))
(let [id (volatile! #?(:cljs (.getTime (js/Date.)) :default 0))]
  (rf/reg-cofx :generators/toast-id (fn [cofx _]
                                      (assoc cofx :toasts/id (vswap! id inc)))))


;; ROUTING
(rf/reg-sub :routing/route (store.subs/get-path [:routing/route]))
(rf/reg-event-db :routing/navigate (store.events/assoc-path [:routing/route]))


;; RESOURCE
(rf/reg-sub :resources/resource store.subs/resource)
(rf/reg-event-fx :resources/submit! store.effects/submit-resource)
(rf/reg-event-db :resources/succeeded store.events/resource-succeeded)
(rf/reg-event-db :resources/failed store.events/resource-failed)
(rf/reg-event-db :resources/destroy store.events/destroy-resource)
(rf/reg-event-db :resources.tags/from-note store.events/add-tags)
(rf/reg-event-db :resources.contexts/from-note store.events/add-context)


;; API
(rf/reg-event-fx :api.tags/select store.effects/fetch-tags)
(rf/reg-event-fx :api.contexts/select store.effects/fetch-contexts)
(rf/reg-event-fx :api.notes/select store.effects/search-notes)
(rf/reg-event-fx :api.notes/find store.effects/fetch-note)
(rf/reg-event-fx :api.notes/create! store.effects/create-note!)
(rf/reg-event-fx :api.notes/update! store.effects/update-note!)


;; FORM
(rf/reg-sub :forms/form store.subs/form)
(rf/reg-event-db :forms/create store.events/create-form)
(rf/reg-event-db :forms/destroy store.events/destroy-form)
(rf/reg-event-db :forms/change store.events/change-form)


;; TOASTS
(rf/reg-sub :toasts/toasts store.subs/toasts)
(rf/reg-event-fx :toasts/success store.toasts/on-success)
(rf/reg-event-fx :toasts/failure store.toasts/on-failure)
(rf/reg-event-fx :toasts/create
                 [(rf/inject-cofx :generators/toast-id)]
                 store.toasts/create)
(rf/reg-event-fx :toasts/hide store.toasts/hide)
(rf/reg-event-db :toasts/show store.toasts/show)
(rf/reg-event-db :toasts/destroy store.toasts/destroy)
