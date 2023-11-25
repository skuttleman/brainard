(ns brainard.ui.store.core
  (:require
    [brainard.ui.store.effects :as store.effects]
    [brainard.ui.store.events :as store.events]
    [brainard.ui.store.subscriptions :as store.subs]
    [re-frame.core :as rf]))

(def ^:private empty-store
  {:tags     [:init]
   :contexts [:init]
   :routing/route nil})

(def ^{:arglists '([[type :as event]])} dispatch rf/dispatch)
(def ^{:arglists '([[type :as event]])} dispatch-sync rf/dispatch-sync)
(def ^{:arglists '([[type :as query]])} subscribe rf/subscribe)


;; CORE
(rf/reg-event-db :core/init (constantly empty-store))
(rf/reg-sub :core/tags (store.subs/get-path [:tags]))
(rf/reg-sub :core/contexts (store.subs/get-path [:contexts]))


;; ROUTING
(rf/reg-sub :routing/route (store.subs/get-path [:routing/route]))
(rf/reg-event-db :routing/navigate (store.events/assoc-path [:routing/route]))


;; API
(rf/reg-event-fx :api.tags/fetch store.effects/fetch-tags)
(rf/reg-event-fx :api.contexts/fetch store.effects/fetch-contexts)
(rf/reg-event-fx :api/create-note! store.effects/create-note!)
(rf/reg-event-fx :api/update-note! store.effects/update-note!)


;; FORM
(rf/reg-sub :forms/value store.subs/form-value)
(rf/reg-sub :forms/errors store.subs/form-errors)
(rf/reg-sub :forms/changed? store.subs/form-changed?)
(rf/reg-sub :forms/touched? store.subs/form-touched?)
(rf/reg-event-db :forms/create store.events/create-form)
(rf/reg-event-db :forms/destroy store.events/destroy-form)
(rf/reg-event-db :forms/change store.events/change-form)
(rf/reg-event-db :forms/touch store.events/touch-form)
