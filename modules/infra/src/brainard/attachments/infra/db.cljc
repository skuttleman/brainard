(ns brainard.attachments.infra.db
  (:require
    [brainard.api.storage.interfaces :as istorage]
    [brainard.attachments.api.core :as api.attachments]))

(defmethod istorage/->input ::api.attachments/save-attachment!
  [{attachment-id :attachments/id :attachments/keys [content-type stream] :as attachment}]
  [{:op      :PutObject
    :request {:Key         (str attachment-id)
              :ContentType content-type
              :Metadata    (select-keys attachment #{:attachments/content-type
                                                     :attachments/filename
                                                     :attachments/size})
              :Body        stream}}])

(defmethod istorage/->input ::api.attachments/get-attachment
  [{attachment-id :attachments/id}]
  {:op      :GetObject
   :request {:Key (str attachment-id)}})
