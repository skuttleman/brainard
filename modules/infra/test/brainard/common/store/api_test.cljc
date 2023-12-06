(ns brainard.common.store.api-test
  (:require
    [brainard.common.store.api :as store.api]
    [brainard.test.utils.common :as tu]
    [clojure.core.async :as async]
    [clojure.test :refer [deftest is testing]]
    [defacto.core :as defacto]))

(deftest request!-test
  (testing "when making a request"
    (tu/async done
      (async/go
        (let [params {:req          {:request-method :post
                                     :url            "/some/url"
                                     :body           (pr-str {:some :edn})
                                     :headers        {"content-type" "application/edn"}}
                      :ok-commands  [[:command-1]]
                      :ok-events    [[:event-1] [:event-2 {:some :details}]]
                      :err-commands [[:error-command]]
                      :err-events   [[:error-event]]}
              commands (atom [])
              events (atom [])
              store (reify
                      defacto/IStore
                      (-dispatch! [_ command]
                        (swap! commands conj command)))
              emit-cb (partial swap! events conj)]
          (testing "and when the request succeeds"
            (#?(:cljs async/<! :default do)
              (defacto/command-handler {::defacto/store store
                                        :services/http  (constantly
                                                          {:status 200
                                                           :body   {:data {:some :data}}})}
                                       [::store.api/request! params]
                                       emit-cb))

            (testing "dispatches success event"
              (is (= [[:command-1 {:some :data}]] @commands))
              (is (= [[:event-1 {:some :data}] [:event-2 {:some :details} {:some :data}]] @events))))

          (testing "and when the request fails"
            (reset! events [])
            (reset! commands [])
            (#?(:cljs async/<! :default do)
              (defacto/command-handler {::defacto/store store
                                        :services/http  (constantly {:status 400
                                                                     :body   {:errors [{:message "bad"
                                                                                        :code    :BAD}]}})}
                                       [::store.api/request! params]
                                       emit-cb))


            (testing "dispatches error event"
              (is (= [[:error-command
                       [{:code    :BAD
                         :message "bad"}]]]
                     @commands))
              (is (= [[:error-event
                       [{:code    :BAD
                         :message "bad"}]]]
                     @events)))))
        (done)))))
