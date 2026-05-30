(ns brainard.infra.obj.store
  (:require
    [brainard.api.storage.interfaces :as istorage]
    [cognitect.aws.client.api :as aws]
    [cognitect.aws.credentials :as aws.creds]))

(deftype ObjStore [invoker]
  istorage/IRead
  (read [_ params]
    (invoker params))

  istorage/IWrite
  (write! [_ params]
    (run! invoker params)))

(defn ->invoker [{:keys [bucket region access-key secret-key]} ->client invoke-fn]
  (let [client (->client {:api                  :s3
                          :region               region
                          :credentials-provider (aws.creds/basic-credentials-provider
                                                  {:access-key-id     access-key
                                                   :secret-access-key secret-key})})]
    (fn [req]
      (let [result (invoke-fn client (assoc-in req [:request :Bucket] bucket))]
        (when-let [ex (some->> result
                               (filter (comp #{"throwable"} name key))
                               first
                               val)]
          (throw ex))
        (when-let [err (:Error result)]
          (throw (ex-info "failed to upload" err)))
        result))))

(defn ->invoke-fn [cfg]
  (->invoker cfg aws/client aws/invoke))
