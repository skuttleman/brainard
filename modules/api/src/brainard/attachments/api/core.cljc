(ns brainard.attachments.api.core
  (:require
    [brainard.api.storage.core :as storage]
    [brainard.api.utils.uuids :as uuids]))

(defn upload! [attachments-api attachments]
  (let [requests (for [attachment attachments
                       :let [attachment-id (uuids/random)]]
                   (assoc attachment
                          :attachments/id attachment-id
                          :attachments/name (:attachments/filename attachment)))]
    (doseq [request requests
            :let [attachment (select-keys request #{:attachments/content-type
                                                    :attachments/filename
                                                    :attachments/id
                                                    :attachments/name})]]
      (storage/execute! (:store attachments-api)
                        (assoc attachment ::storage/type ::create!))
      (storage/execute! (:obj-store attachments-api)
                        (assoc request ::storage/type ::upload!)))
    (for [request requests]
      (select-keys request #{:attachments/content-type
                             :attachments/filename
                             :attachments/id}))))

(defn fetch [attachments-api attachment-id]
  (let [{:keys [Body ContentType ContentLength]} (storage/query (:obj-store attachments-api)
                                                                {::storage/type  ::download
                                                                 :attachments/id attachment-id})]
    (when Body
      {:attachments/id             attachment-id
       :attachments/content-length ContentLength
       :attachments/content-type   ContentType
       :attachments/stream         Body})))
