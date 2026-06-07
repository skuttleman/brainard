(ns brainard.events.infra.routes-test
  (:require
    [brainard :as-alias b]
    [brainard.api.events.interfaces :as ievents]
    [brainard.events.infra.routes :as routes]
    [brainard.events.infra.manager :as manager]
    [clojure.test :refer [deftest is testing]]))

(deftest handle-events-test
  (testing "when responding to an events request"
    (let [send-msgs (atom [])
          send-fn (comp (partial swap! send-msgs conj) vector)
          manager (manager/create send-fn nil)
          result (routes/handle-events {::b/events manager}
                                       (fn [_ handler]
                                         (assoc handler :status 200))
                                       send-fn)]
      (testing "returns the correct headers"
        (is (= "text/event-stream" (get-in result [:headers "content-type"]))))

      (testing "handles the channel open event"
        ((:on-open result) :fake-ch)
        (is (= [[:fake-ch "event: connected\n\n"]] @send-msgs)))

      (testing "handles the channel close event"
        (reset! send-msgs [])
        ((:on-close result) :fake-ch nil)
        (ievents/broadcast! manager :some-msg)
        (is (= [] @send-msgs))))))
