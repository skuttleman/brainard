(ns brainard.common.store.api-test
  (:require
    [brainard.common.store.api :as store.api]
    [brainard.test.utils.common :as tu]
    [brainard.test.utils.spies :as spies]
    [clojure.core.async :as async]
    [clojure.test :refer [deftest is testing]]
    [yast.core :as yast]))

(deftest do-request-test
  (testing "when making a request"
    (tu/async done
      (async/go
        (let [params {:on-success-n [[:event-1] [:event-2 {:some :details}]]
                      :on-error-n   [[:error-event]]
                      :params       {:query-params {:some :param}}
                      :method       :post
                      :route        :routes.api/notes}
              calls (atom [])
              store (reify
                      yast/IStore
                      (-dispatch! [_ command]
                        (swap! calls conj command)))]
          (testing "and when the request succeeds"
            (binding [store.api/*request-fn* (spies/on
                                               (constantly
                                                 (async/go
                                                   {:status 200
                                                    :body   {:data {:some :data}}})))]
              (async/<! (store.api/request! store (assoc params :body {:ok? true})))
              (testing "makes an HTTP call"
                (let [request (ffirst (spies/calls store.api/*request-fn*))]
                  (is (= {:request-method :post
                          :url            "/api/notes?some=param"
                          :body           "{:ok? true}"
                          :headers        {"content-type" "application/edn"}}
                         request)))))

            (testing "dispatches success event"
              (let [[event-1 event-2 & more-events] @calls]
                (is (= event-1 [:event-1 {:some :data}]))
                (is (= event-2 [:event-2 {:some :details} {:some :data}]))
                (is (empty? more-events)))))

          (testing "and when the request fails"
            (binding [store.api/*request-fn* (spies/on
                                               (constantly (async/go
                                                             {:status 400
                                                              :body   {:errors [{:message "bad"
                                                                                 :code    :BAD}]}})))]
              (reset! calls [])
              (async/<! (store.api/request! store (assoc params :body {:ok? false})))
              (testing "makes an HTTP call"
                (let [request (ffirst (spies/calls store.api/*request-fn*))]
                  (is (= {:request-method :post
                          :url            "/api/notes?some=param"
                          :body           "{:ok? false}"
                          :headers        {"content-type" "application/edn"}}
                         request)))))

            (testing "dispatches error event"
              (let [[event & more-events] @calls]
                (is (= event [:error-event [{:message "bad"
                                             :code    :BAD}]]))
                (is (empty? more-events))))))
        (done)))))
