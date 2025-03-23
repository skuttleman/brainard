(ns brainard.attachments.api.core
  (:require
    [brainard.api.storage.core :as storage]
    [brainard.api.utils.uuids :as uuids]))

(defn upload! [attachments-api uploads]
  (doall (for [upload uploads
               :let [attachment-id (uuids/random)
                     upload (assoc upload :attachments/id attachment-id)
                     attachment (-> upload
                                    (select-keys #{:attachments/id
                                                   :attachments/content-type
                                                   :attachments/filename})
                                    (assoc :attachments/name (:attachments/filename upload)))]]
           (do (storage/execute! (:store attachments-api)
                                 (assoc attachment ::storage/type ::create!))
               (storage/execute! (:obj-store attachments-api)
                                 (assoc upload ::storage/type ::upload!))
               attachment))))

(defn fetch [attachments-api attachment-id]
  (let [{:keys [Body ContentType ContentLength]} (storage/query (:obj-store attachments-api)
                                                                {::storage/type  ::download
                                                                 :attachments/id attachment-id})]
    (when Body
      {:attachments/id             attachment-id
       :attachments/content-length ContentLength
       :attachments/content-type   ContentType
       :attachments/stream         Body})))
