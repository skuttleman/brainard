(ns brainard.common.store.api-test
  (:require
    [brainard.common.store.api :as store.api]
    [brainard.test.utils.common :as tu]
    [brainard.test.utils.spies :as spies]
    [clojure.core.async :as async]
    [clojure.test :refer [deftest is testing]]
    [defacto.core :as defacto]))

(deftest do-request-test #_request!-test
  (testing "when making a request"
    (tu/async done
      (async/go
        (let [params {::store.api/spec :api.notes/select!
                      :params          {:query-params {:some :param}}
                      :on-success-n    [[:event-1] [:event-2 {:some :details}]]
                      :on-error-n      [[:error-event]]}
              calls (atom [])
              store (reify
                      defacto/IStore
                      (-dispatch! [_ command]
                        (swap! calls conj command)))]
          (testing "and when the request succeeds"
            (let [request-fn (spies/on
                               (constantly
                                 {:status 200
                                  :body   {:data {:some :data}}}))]
              (#?(:cljs async/<! :default do) (store.api/request! store request-fn params))
              (testing "makes an HTTP call"
                (let [request (ffirst (spies/calls request-fn))]
                  (is (= {:request-method :get
                          :url            "/api/notes?some=param"
                          :body           nil
                          :headers        {"content-type" "application/edn"}}
                         request)))))

            (testing "dispatches success event"
              (let [events (into {} (map (juxt first identity)) @calls)]
                (is (= (:event-1 events) [:event-1 {:some :data}]))
                (is (= (:event-2 events) [:event-2 {:some :details} {:some :data}]))
                (is (not (contains? events :error-event))))))

          (testing "and when the request fails"
            (let [request-fn (spies/on
                               (constantly {:status 400
                                            :body   {:errors [{:message "bad"
                                                               :code    :BAD}]}}))]
              (reset! calls [])
              (#?(:cljs async/<! :default do) (store.api/request! store request-fn params))
              (testing "makes an HTTP call"
                (let [request (ffirst (spies/calls request-fn))]
                  (is (= {:request-method :get
                          :url            "/api/notes?some=param"
                          :body           nil
                          :headers        {"content-type" "application/edn"}}
                         request)))))

            (testing "dispatches error event"
              (let [events (into {} (map (juxt first identity)) @calls)]
                (is (= (:error-event events) [:error-event [{:message "bad"
                                                             :code    :BAD}]]))
                (is (not (contains? events :event-1)))
                (is (not (contains? events :event-2)))))))
        (done)))))
