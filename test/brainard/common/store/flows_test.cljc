(ns brainard.common.store.flows-test
  (:require
    [brainard.common.resources.specs :as-alias rspecs]
    [brainard.common.store.core :as store]
    [brainard.common.stubs.nav :as nav]
    [brainard.common.utils.routing :as rte]
    [brainard.common.utils.uuids :as uuids]
    [clojure.test :refer [deftest is testing]]
    [defacto.core :as defacto]
    brainard.common.store.commands
    brainard.common.store.events
    brainard.common.store.queries))

(deftype NavStub [^:volatile-mutable -store]
  defacto/IInitialize
  (init! [_ store]
    (set! -store store))

  nav/INavigate
  (-set! [_ uri]
    (store/emit! -store [:routing/navigated (rte/match uri)])))

(deftest nav-test
  (let [nav (->NavStub nil)
        store (store/create {:services/nav nav})]
    (testing "when navigating"
      (testing "navigates to a string uri"
        (nav/navigate! nav "/some/uri")
        (is (= {:token :routes.ui/not-found
                :uri   "/some/uri"}
               (select-keys (store/query store [:routing/?:route])
                            #{:token :uri}))))

      (testing "navigates to a route token"
        (let [note-id (uuids/random)]
          (nav/navigate! nav :routes.api/note {:notes/id note-id})
          (is (= {:route-params {:notes/id note-id}
                  :token        :routes.api/note
                  :uri          (str "/api/notes/" note-id)}
                 (select-keys (store/query store [:routing/?:route])
                              #{:route-params :token :uri}))))))))

(deftest toast-test
  (testing "when creating a toast"
    (let [store (store/create)]
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
