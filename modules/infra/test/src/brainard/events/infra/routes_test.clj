(ns brainard.events.infra.routes-test
  (:require
   [brainard :as-alias b]
   [brainard.api.events.interfaces :as ievents]
   [brainard.events.infra.routes :as routes]
   [brainard.events.infra.manager :as manager]
   [clojure.test :refer [deftest is testing]]
   [slag.test.utils.async :as tua]))

(deftest handle-events-test
  (testing "when responding to an events request"
    (let [manager (manager/create 100)
          {ch :body :as result} (routes/handle-events {::b/events manager}
                                                      (fn [ch]
                                                        [:ch-id ch])
                                                      identity)]
      (testing "returns the correct headers"
        (is (= {"Content-Type"  "text/event-stream"
                "Cache-Control" "no-cache"
                "Connection"    "keep-alive"}
               (:headers result))))

      (testing "opens the connection"
        (let [msg (tua/<!! ch)]
          (is (= "event: connected\n\n" msg))))

      (testing "and when closing the manager"
        (ievents/close! manager)

        (testing "closes the channel"
          (is (nil? (tua/<!! ch))))))))
