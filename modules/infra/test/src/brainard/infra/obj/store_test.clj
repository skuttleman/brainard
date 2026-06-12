(ns brainard.infra.obj.store-test
  (:require
   [brainard.infra.obj.store :as os]
   [clojure.test :refer [deftest is testing]]
   [cognitect.aws.credentials :as aws.creds]
   [slag.test.utils.spies :as spies]))

(deftest ->invoker-test
  (testing "when creating an invoke-fn"
    (let [client-fn identity
          invoke-fn (spies/create (fn [_ val]
                                    (cond-> val
                                      (instance? Throwable val) throw)))
          cfg {:access-key "some-access-key"
               :bucket     "some-bucket"
               :region     "some-region"
               :secret-key "some-secret-key"}
          invoker (os/->invoker cfg client-fn invoke-fn)]
      (testing "and when the invocation succeeds"
        (let [result (invoker {:request {:some :request}})
              [client-cfg req] (first (spies/calls invoke-fn))]
          (testing "calls invoke-fn"
            (is (= :s3 (:api client-cfg)))
            (is (= "some-region" (:region client-cfg)))
            (is (= {:aws/access-key-id     "some-access-key"
                    :aws/secret-access-key "some-secret-key"}
                   (aws.creds/fetch (:credentials-provider client-cfg))))
            (is (= req {:request {:some   :request
                                  :Bucket "some-bucket"}})))

          (testing "returns the result"
            (is (= req result))))

        (testing "and when the invocation throws"
          (testing "throws an exception"
            (is (thrown? Throwable (invoker (ex-info "boom" {})))))))

      (testing "and when the invocation errors"
        (testing "throws an exception"
          (is (thrown? Throwable (invoker {:request        {:some :request}
                                           :some/throwable (ex-info "boom" {})})))

          (is (thrown? Throwable (invoker {:request {:some :request}
                                           :Error   {:some :error}}))))))))
