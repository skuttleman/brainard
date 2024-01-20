(ns brainard.infra.store-test
  (:require
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.store.core :as store]
    [clojure.test :refer [deftest is testing]]
    [defacto.core :as defacto]
    brainard.infra.store.commands
    brainard.infra.store.events
    brainard.infra.store.queries))

(deftest toast-test
  (testing "when creating a toast"
    (let [store (defacto/create {} {})]
      (store/dispatch! store [:toasts/create! :success [:div "toast body"]])
      (testing "and when querying the db"
        (let [{toast-id :id} (first (store/query store [:toasts/?:toasts]))
              toast (store/subscribe store [:toasts/?:toast toast-id])]

          (testing "contains the new toast"
            (is (some? toast-id))
            (is (= {:id    toast-id
                    :level :success
                    :body  [:div "toast body"]
                    :state :init}
                   @toast)))

          (testing "and when showing the toast"
            (store/emit! store [:toasts/shown toast-id])
            (testing "the toast is visible"
              (is (= :visible (:state @toast))))

            (testing "and when hiding the toast"
              (store/dispatch! store [:toasts/hide! toast-id])
              (testing "the toast is hidden"
                (is (= :hidden (:state @toast))))

              (testing "and when destroying the toast"
                (store/emit! store [:toasts/destroyed toast-id])

                (testing "the toast is destroyed"
                  (is (nil? @toast)))))))))))
