(ns brainard.infra.store-test
  (:require
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.store.core :as store]
    [whet.navigation :as nav]
    [whet.interfaces :as iwhet]
    [brainard.infra.utils.routing :as rte]
    [brainard.api.utils.uuids :as uuids]
    [clojure.test :refer [deftest is testing]]
    [defacto.core :as defacto]
    brainard.infra.store.commands
    brainard.infra.store.events
    brainard.infra.store.queries))

(deftype NavStub [^:volatile-mutable -store]
  defacto/IInitialize
  (init! [_ store]
    (set! -store store))

  iwhet/INavigate
  (replace! [_ token route-params query-params]
    (store/emit! -store [:whet.core/navigated {:token        token
                                               :route-params route-params
                                               :query-params query-params}])))

(deftest nav-test
  (let [nav (->NavStub nil)
        store (store/create {:services/nav nav})]
    (testing "when navigating"
      (testing "navigates to a string uri"
        (nav/navigate! nav "/some/uri")
        (is (= {:token :routes.ui/not-found
                :uri   "/some/uri"}
               (select-keys (store/query store [:whet.core/?:route])
                            #{:token :uri}))))

      (testing "navigates to a route token"
        (let [note-id (uuids/random)]
          (nav/navigate! nav :routes.api/note {:notes/id note-id})
          (is (= {:route-params {:notes/id note-id}
                  :token        :routes.api/note
                  :uri          (str "/api/notes/" note-id)}
                 (select-keys (store/query store [:whet.core/?:route])
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
