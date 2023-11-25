(ns brainard.ui.services.store.core
  (:require
    [brainard.ui.services.store.effects :as store.effects]
    [brainard.ui.services.store.events :as store.events]
    [brainard.ui.services.store.subscriptions :as store.subs]
    [re-frame.core :as rf*]))

(def ^:private empty-store
  {:tags     [:init]
   :contexts [:init]
   :routing/route nil})

;; CORE
(rf*/reg-event-db :core/init (constantly empty-store))
(rf*/reg-sub :core/tags (store.subs/get-path [:tags]))
(rf*/reg-sub :core/contexts (store.subs/get-path [:contexts]))


;; ROUTING
(rf*/reg-sub :routing/route (store.subs/get-path [:routing/route]))
(rf*/reg-event-db :routing/navigate (store.events/assoc-path [:routing/route]))


;; API
(rf*/reg-event-fx :api.tags/fetch store.effects/fetch-tags)
(rf*/reg-event-fx :api.contexts/fetch store.effects/fetch-contexts)
(rf*/reg-event-fx :api.notes/create! store.effects/create-note!)
(rf*/reg-event-fx :api.notes/update! store.effects/update-note!)


;; FORM
(rf*/reg-sub :forms/value store.subs/form-value)
(rf*/reg-sub :forms/changed? store.subs/form-changed?)
(rf*/reg-sub :forms/touched? store.subs/form-touched?)
(rf*/reg-sub :forms/status store.subs/form-status)
(rf*/reg-sub :forms/errors store.subs/form-errors)
(rf*/reg-sub :forms/warnings store.subs/form-warnings)
(rf*/reg-event-db :forms/create store.events/create-form)
(rf*/reg-event-db :forms/destroy store.events/destroy-form)
(rf*/reg-event-db :forms/change store.events/change-form)
(rf*/reg-event-db :forms/touch store.events/touch-form)

(rf*/reg-event-db :forms/submit store.events/submit-form)
(rf*/reg-event-db :forms/invalid store.events/form-invalid)
(rf*/reg-event-db :forms/succeeded store.events/create-form)
(rf*/reg-event-db :forms/failed store.events/form-failed)
