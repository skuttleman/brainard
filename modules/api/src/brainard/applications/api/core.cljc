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
                                                            :companies/website})
                (assoc :applications/id app-id
                       :applications/state :ACTIVE))]
    (storage/execute! (:store apps-api) (assoc app ::storage/type ::create!))
    (storage/query (:store apps-api)
                   {::storage/type   ::get-app
                    :applications/id app-id})))

(defn update! [apps-api app-id app]
  (let [app (-> app
                (select-keys #{:applications/company :applications/details :applications/job-title})
                (update :applications/company select-keys #{:companies/location
                                                            :companies/name
                                                            :companies/website})
                (assoc :applications/id app-id))]
    (storage/execute! (:store apps-api) (assoc app ::storage/type ::update!))
    (storage/query (:store apps-api)
                   {::storage/type   ::get-app
                    :applications/id app-id})))

(defn fetch [apps-api app-id]
  (storage/query (:store apps-api) {::storage/type   ::get-app
                                    :applications/id app-id}))

(defn select [apps-api]
  (->> (storage/query (:store apps-api) {::storage/type ::get-apps})
       (sort-by :applications/updated-at #(compare %2 %1))))
