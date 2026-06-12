(ns brainard.infra.routes.errors-test
  (:require
   [brainard.api.validations :as-alias valid]
   [brainard.infra.routes.errors :as routes.err]
   [clojure.test :refer [are deftest testing]]
   [slag.utils.edn :as edn]))

(deftest ex->response-test
  (testing "when generating an error response"
    (are [msg type details expected] (testing msg
                                       (= expected
                                          (-> (routes.err/ex->response {::valid/type type
                                                                        :details     details})
                                              (update :body edn/read-string))))

      "handles invalid input"
      ::valid/input-validation {:foo :bar}
      {:status  400
       :headers {"content-type" "application/edn"}
       :body    {:errors [{:details {:foo :bar}
                           :message "Invalid input"
                           :code    :INVALID_INPUT}]}}

      "handles large files"
      ::valid/upload-too-big {}
      {:status  400
       :headers {"content-type" "application/edn"}
       :body    {:errors [{:details {}
                           :message "A file being uploaded exceeds the maximum allowed size"
                           :code    :FILE_UPLOAD_EXCEEDS_MAX_ALLOWED}]}}

      "handles unknown errors"
      ::valid/unknown {:a 1}
      {:status  500
       :headers {"content-type" "application/edn"}
       :body    {:errors [{:message "An unknown error occurred"
                           :code    :UNKNOWN}]}})))
