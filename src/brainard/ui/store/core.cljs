(ns brainard.ui.store.core
  (:require
    [brainard.ui.store.events :as store.events]
    [brainard.common.forms :as forms]
    [re-frame.core :as rf]))

(def ^{:arglists '([[type :as event]])} dispatch rf/dispatch)
(def ^{:arglists '([[type :as event]])} dispatch-sync rf/dispatch-sync)
(def ^{:arglists '([[type :as query]])} subscribe rf/subscribe)

(rf/reg-sub :core/tags (fn [db _] (:tags db)))
(rf/reg-sub :core/contexts (fn [db _] (:contexts db)))

(rf/reg-event-db
  :core/init
  (constantly {:tags     [:init]
               :contexts [:init]}))


;; API
(rf/reg-event-fx :api.tags/fetch store.events/fetch-tags)
(rf/reg-event-fx :api.contexts/fetch store.events/fetch-contexts)
(rf/reg-event-fx :api/create-note! store.events/create-note!)
(rf/reg-event-fx :api/update-note! store.events/update-note!)


;; FORM
(rf/reg-sub :form/value
            (fn [db [_ id]]
              (forms/current (get-in db [::forms id]))))
(rf/reg-sub :form/errors
            (fn [db [_ id]]
              (forms/errors (get-in db [::forms id]))))
(rf/reg-sub :form/changed?
            (fn [db [_ id path :as action]]
              (let [form (get-in db [::forms id])]
                (if (= 2 (count action))
                  (forms/changed? form)
                  (forms/changed? form path)))))
(rf/reg-sub :form/touched?
            (fn [db [_ id path :as action]]
              (let [form (get-in db [::forms id])]
                (if (= 2 (count action))
                  (forms/touched? form)
                  (forms/touched? form path)))))
(rf/reg-event-db :form/create
                 (fn [db [_ id data]]
                   (assoc-in db [::forms id] (forms/create id data))))
(rf/reg-event-db :form/destroy
                 (fn [db [_ id]]
                   (update db ::forms dissoc id)))
(rf/reg-event-db :form/modify
                 (fn [db [_ id path value]]
                   (update-in db [::forms id]
                              (fn [form]
                                (-> form
                                    (forms/change path value)
                                    (forms/touch path))))))
(rf/reg-sub :form/touch
            (fn [db [_ id path :as action]]
              (let [form (get-in db [::forms id])]
                (if (= 2 (count action))
                  (forms/touch form)
                  (forms/touch form path)))))
