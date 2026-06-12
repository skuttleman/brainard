(ns brainard.infra.store.events-test
  (:require
   [brainard.infra.store.core :as store]
   [brainard.infra.store.specs :as specs]
   [clojure.test :refer [deftest is testing]]
   [defacto.core :as defacto]
   [defacto.resources.core :as res]
   brainard.infra.store.events
   brainard.infra.store.queries))

(defn ^:private loaded-store [spec payload]
  (-> (defacto/create {} {})
      (store/emit! [::res/submitted spec])
      (store/emit! [::res/succeeded spec payload])))

(deftest modals-test
  (testing "when creating a modal"
    (let [store (defacto/create {} {})]
      (store/emit! store [:modals/created 1 {:state :init :body [:div "modal"]}])
      (testing "the modal exists"
        (is (= [{:id 1 :state :init :body [:div "modal"]}]
               (store/query store [:modals/?:modals]))))

      (testing "and when displaying the modal"
        (store/emit! store [:modals/displayed 1])
        (testing "the modal state is :displayed"
          (is (= :displayed (:state (first (store/query store [:modals/?:modals])))))))

      (testing "and when hiding the modal"
        (store/emit! store [:modals/hidden 1])
        (testing "the modal state is :hidden"
          (is (= :hidden (:state (first (store/query store [:modals/?:modals])))))))

      (testing "and when destroying the modal"
        (store/emit! store [:modals/destroyed 1])
        (testing "the modal is gone"
          (is (empty? (store/query store [:modals/?:modals])))))))

  (testing "when displaying a modal that does not exist"
    (let [store (defacto/create {} {})]
      (store/emit! store [:modals/displayed 99])
      (testing "no modal is created"
        (is (empty? (store/query store [:modals/?:modals]))))))

  (testing "when hiding a modal that does not exist"
    (let [store (defacto/create {} {})]
      (store/emit! store [:modals/hidden 99])
      (testing "no modal is created"
        (is (empty? (store/query store [:modals/?:modals]))))))

  (testing "when hiding all modals"
    (let [store (defacto/create {} {})]
      (store/emit! store [:modals/created 1 {:state :displayed :body [:div "one"]}])
      (store/emit! store [:modals/created 2 {:state :displayed :body [:div "two"]}])
      (store/emit! store [:modals/all-hidden])
      (testing "all modals are hidden"
        (is (every? #(= :hidden (:state %)) (store/query store [:modals/?:modals]))))))

  (testing "when destroying all modals"
    (let [store (defacto/create {} {})]
      (store/emit! store [:modals/created 1 {:state :hidden :body [:div "one"]}])
      (store/emit! store [:modals/created 2 {:state :hidden :body [:div "two"]}])
      (store/emit! store [:modals/all-destroyed])
      (testing "all modals are gone"
        (is (empty? (store/query store [:modals/?:modals])))))))

(deftest api-notes-saved-test
  (testing "when tags and contexts resources are not loaded"
    (let [store (defacto/create {} {})]
      (-> store
          (store/emit! [::res/destroyed [::specs/tags#select]])
          (store/emit! [::res/destroyed [::specs/contexts#select]])
          (store/emit! [:api.notes/saved {:notes/context "ctx" :notes/tags #{:a}}]))
      (testing "no resources are created"
        (is (res/init? (store/query store [::res/?:resource [::specs/tags#select]])))
        (is (res/init? (store/query store [::res/?:resource [::specs/contexts#select]]))))))

  (testing "when the tags resource is loaded"
    (let [store (loaded-store [::specs/tags#select] #{:existing})]
      (store/emit! store [:api.notes/saved {:notes/tags #{:new-a :new-b}}])
      (testing "the new tags are merged into the cache"
        (is (= #{:existing :new-a :new-b}
               (res/payload (store/query store [::res/?:resource [::specs/tags#select]])))))))

  (testing "when the contexts resource is loaded and a context is present"
    (let [store (loaded-store [::specs/contexts#select] #{"existing-ctx"})]
      (store/emit! store [:api.notes/saved {:notes/context "new-ctx" :notes/tags #{}}])
      (testing "the new context is added to the cache"
        (is (contains? (res/payload (store/query store [::res/?:resource [::specs/contexts#select]]))
                       "new-ctx")))))

  (testing "when the contexts resource is loaded but context is nil"
    (let [store (loaded-store [::specs/contexts#select] #{"existing-ctx"})]
      (store/emit! store [:api.notes/saved {:notes/context nil :notes/tags #{}}])
      (testing "the contexts cache is unchanged"
        (is (= #{"existing-ctx"}
               (res/payload (store/query store [::res/?:resource [::specs/contexts#select]]))))))))
