(ns brainard.common.store.flows-test
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.store.core :as store]
    [clojure.test :refer [are deftest is testing]]
    brainard.common.store.commands
    brainard.common.store.events
    brainard.common.store.queries))

(deftest form-test
  (testing "when creating a form"
    (let [store (store/create)]
      (store/dispatch! store [:forms/ensure! 123 {:fruit :apple}])
      (testing "and when querying the db"
        (testing "has the form data"
          (is (= {:fruit :apple} (forms/data (store/query store [:forms/?:form 123]))))))

      (testing "and when recreating a form"
        (store/dispatch! store [:forms/ensure! 123 {:random? true}])
        (testing "and when querying the db"
          (testing "retains the original form data"
            (is (= {:fruit :apple} (forms/data (store/query store [:forms/?:form 123])))))))

      (testing "and when updating the form"
        (store/emit! store [:forms/changed 123 [:fruit] :banana])
        (store/emit! store [:forms/changed 123 [:nested :prop] -13])
        (testing "has the updated form data"
          (is (= {:fruit  :banana
                  :nested {:prop -13}}
                 (forms/data (store/query store [:forms/?:form 123]))))))

      (testing "and when destroying the form"
        (store/emit! store [:forms/destroyed 123])

        (testing "no longer has form data"
          (is (nil? (forms/data (store/query store [:forms/?:form 123])))))))))

(deftest note-creation-test)

(deftest note-update-test)

(deftest toast-test)

(deftest resource-test)
