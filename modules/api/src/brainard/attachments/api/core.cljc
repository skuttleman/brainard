(ns brainard.attachments.api.core
  (:require
    [brainard.api.storage.core :as storage]
    [brainard.api.utils.uuids :as uuids]))

(defn upload! [attachments-api attachments]
  (let [requests (for [attachment attachments
                       :let [attachment-id (uuids/random)]]
                   (assoc attachment
                          ::storage/type ::save-attachment!
                          :attachments/id attachment-id))]
    (apply storage/execute! (:obj-store attachments-api) requests)
    (for [request requests]
      (select-keys request #{:attachments/content-type
                             :attachments/filename
                             :attachments/id}))))

(defn fetch [attachments-api attachment-id]
  (storage/query (:obj-store attachments-api)
                 {::storage/type  ::get-attachment
                  :attachments/id attachment-id}))
