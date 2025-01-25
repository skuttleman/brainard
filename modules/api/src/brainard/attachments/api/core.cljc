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
    (run! (partial storage/execute! (:obj-store attachments-api)) requests)
    (for [request requests]
      (-> request
          (select-keys #{:attachments/content-type
                         :attachments/filename
                         :attachments/id})
          (as-> $ (assoc $ :attachments/name (:attachments/filename $)))))))

(defn fetch [attachments-api attachment-id]
  (let [{:keys [Body ContentType ContentLength]} (storage/query (:obj-store attachments-api)
                                                                {::storage/type  ::get-attachment
                                                                 :attachments/id attachment-id})]
    {:attachments/id             attachment-id
     :attachments/content-length ContentLength
     :attachments/content-type   ContentType
     :attachments/stream         Body}))
