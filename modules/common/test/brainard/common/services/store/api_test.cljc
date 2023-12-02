(ns brainard.common.services.store.api-test
  (:require
    [brainard.common.services.store.api :as store.api]
    [brainard.common.services.store.api :as store.api]
    [brainard.common.services.store.api :as stores.api]
    [brainard.test.utils.common :as tu]
    [brainard.test.utils.spies :as spies]
    [clojure.core.async :as async]
    [clojure.test :refer [deftest is testing]]
    [re-frame.core :as rf]))

(deftest do-request-test
  (testing "when making a request"
    (with-redefs [rf/dispatch (spies/constantly)]
      (tu/async done
        (async/go
          (let [params {:on-success-n [[:event-1] [:event-2 {:some :details}]]
                        :on-error-n   [[:error-event]]
                        :query-params {:some :param}
                        :method       :post
                        :route        :routes.api/notes}]
            (testing "and when the request succeeds"
              (binding [stores.api/*request-fn* (spies/on
                                                  (constantly
                                                    (async/go
                                                      {:status 200
                                                       :body   {:data {:some :data}}})))]
                (spies/wipe! rf/dispatch)
                (async/<! (stores.api/request-fx (assoc params :body {:ok? true})))
                (testing "makes an HTTP call"
                  (let [request (ffirst (spies/calls store.api/*request-fn*))]
                    (is (= {:request-method :post
                            :url            "/api/notes?some=param"
                            :body           "{:ok? true}"
                            :headers        {"content-type" "application/edn"}}
                           request)))))

              (testing "dispatches success event"
                (let [[event-1 event-2 & more-events] (map first (spies/calls rf/dispatch))]
                  (is (= event-1 [:event-1 {:some :data}]))
                  (is (= event-2 [:event-2 {:some :details} {:some :data}]))
                  (is (empty? more-events)))))

            (testing "and when the request fails"
              (binding [stores.api/*request-fn* (spies/on
                                                  (constantly (async/go
                                                                {:status 400
                                                                 :body   {:errors [{:message "bad"
                                                                                    :code    :BAD}]}})))]
                (spies/wipe! rf/dispatch)
                (async/<! (stores.api/request-fx (assoc params :body {:ok? false})))
                (testing "makes an HTTP call"
                  (let [request (ffirst (spies/calls store.api/*request-fn*))]
                    (is (= {:request-method :post
                            :url            "/api/notes?some=param"
                            :body           "{:ok? false}"
                            :headers        {"content-type" "application/edn"}}
                           request)))))

              (testing "dispatches error event"
                (let [[event & more-events] (map first (spies/calls rf/dispatch))]
                  (is (= event [:error-event [{:message "bad"
                                               :code    :BAD}]]))
                  (is (empty? more-events))))))
          (done))))))
