(ns brainard.attachments.infra.db
  (:require
    [brainard.api.storage.interfaces :as istorage]
    [brainard.attachments.api.core :as api.attachments]))

(defmethod istorage/->input ::api.attachments/create!
  [attachment]
  [(select-keys attachment #{:attachments/id
                             :attachments/content-type
                             :attachments/name
                             :attachments/filename})])

(defmethod istorage/->input ::api.attachments/get-attachment
  [{attachment-id :attachments/id}]
  {:query '[:find (pull ?e [:attachments/id
                            :attachments/name
                            :attachments/filename
                            :attachments/content-type])
            :in $ ?id
            :where [?e :attachments/id ?id]]
   :xform (map first)
   :args [attachment-id]
   :only? true})

(defmethod istorage/->input ::api.attachments/upload!
  [{attachment-id :attachments/id :attachments/keys [content-type stream] :as attachment}]
  [{:op      :PutObject
    :request {:Key         (str attachment-id)
              :ContentType content-type
              :Metadata    (select-keys attachment #{:attachments/content-type
                                                     :attachments/filename
                                                     :attachments/size})
              :Body        stream}}])

(defmethod istorage/->input ::api.attachments/download
  [{attachment-id :attachments/id}]
  {:op      :GetObject
   :request {:Key (str attachment-id)}})
