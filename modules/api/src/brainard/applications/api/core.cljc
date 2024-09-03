(ns brainard.applications.api.core
  (:require
    [brainard.api.storage.core :as storage]
    [brainard.api.utils.uuids :as uuids]))

(defn create! [apps-api app]
  (let [app-id (uuids/random)
        app (-> app
                (select-keys #{:applications/company :applications/details :applications/job-title})
                (update :applications/company select-keys #{:companies/location
                                                            :companies/name
                                                            :companies/website
                                                            :companies/contacts})
                (update-in [:applications/company :companies/contacts]
                           (partial map #(-> %
                                             (select-keys #{:contacts/name
                                                            :contacts/email
                                                            :contacts/phone})
                                             (assoc :contacts/id (uuids/random)))))
                (assoc :applications/id app-id
                       :applications/state :ACTIVE))]
    (storage/execute! (:store apps-api) (assoc app ::storage/type ::create!))
    (storage/query (:store apps-api)
                   {::storage/type   ::get-app
                    :applications/id app-id})))

(defn ^:private contact-diff [curr-contacts next-contacts]
  (let [id->contact (into {}
                          (map (juxt :contacts/id identity))
                          curr-contacts)]
    (loop [updates []
           removals (set (keys id->contact))
           [contact :as contacts] next-contacts]
      (let [{contact-id :contacts/id :as contact} (select-keys contact
                                                               #{:contacts/id
                                                                 :contacts/name
                                                                 :contacts/email
                                                                 :contacts/phone})]
        (if (empty? contacts)
          [updates removals]
          (recur (conj updates (if-let [existing (id->contact contact-id)]
                                 (merge existing contact)
                                 (assoc contact :contacts/id (uuids/random))))
                 (disj removals contact-id)
                 (rest contacts)))))))

(defn update! [apps-api app-id app]
  (let [[updates removals] (contact-diff (storage/query (:store apps-api)
                                                        {::storage/type   ::app-contacts
                                                         :applications/id app-id})
                                         (-> app :applications/company :companies/contacts))
        app (-> app
                (select-keys #{:applications/company :applications/details :applications/job-title})
                (update :applications/company select-keys #{:companies/location
                                                            :companies/name
                                                            :companies/website})
                (assoc-in [:applications/company :companies/contacts] updates)
                (assoc :applications/id app-id))]
    (storage/execute! (:store apps-api)
                      (assoc app ::storage/type ::update!)
                      {::storage/type ::remove-contacts!
                       :contacts/ids  removals})
    (storage/query (:store apps-api)
                   {::storage/type   ::get-app
                    :applications/id app-id})))

(defn fetch [apps-api app-id]
  (storage/query (:store apps-api) {::storage/type   ::get-app
                                    :applications/id app-id}))

(defn select [apps-api]
  (->> (storage/query (:store apps-api) {::storage/type ::get-apps})
       (sort-by :applications/updated-at #(compare %2 %1))))
