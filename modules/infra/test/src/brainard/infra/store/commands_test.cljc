(ns brainard.infra.store.commands-test
  (:require
    [brainard.infra.store.core :as store]
    [brainard.infra.test-utils :as tu]
    [clojure.core.async :as async]
    [clojure.test :refer [deftest is testing]]
    [defacto.core :as defacto]
    [whet.core :as w]
    [whet.interfaces :as iwhet]
    brainard.infra.store.commands
    brainard.infra.store.events
    brainard.infra.store.queries))

(deftest nav-test
  (letfn [(mock-nav [navigate-calls replace-calls]
            (reify iwhet/INavigate
              (navigate! [_ token route-params qp]
                (swap! navigate-calls conj [token route-params qp]))
              (replace! [_ token route-params qp]
                (swap! replace-calls conj [token route-params qp]))))]
    (testing "when calling ::w/with-qp! command"
      (let [navigate-calls (atom [])
            store (defacto/create {::w/nav (mock-nav navigate-calls (atom []))} {})]
        (store/emit! store [:whet.core/navigated {:token        :routes.ui/home
                                                  :route-params {:note/id 1}
                                                  :query-params {}}])
        (store/dispatch! store [::w/with-qp! {:filter "foo"}])
        (testing "navigates to the current route with the updated query params"
          (is (= [[:routes.ui/home {:note/id 1} {:filter "foo"}]] @navigate-calls)))))

    (testing "when calling :nav/navigate! command"
      (let [navigate-calls (atom [])
            store (defacto/create {::w/nav (mock-nav navigate-calls (atom []))} {})]
        (store/dispatch! store [:nav/navigate! {:token        :routes.ui/home
                                                :route-params {:note/id 1}
                                                :query-params {:q "x"}}])
        (testing "navigates to the given route"
          (is (= [[:routes.ui/home {:note/id 1} {:q "x"}]] @navigate-calls)))))

    (testing "when calling :nav/replace! command"
      (let [replace-calls (atom [])
            store (defacto/create {::w/nav (mock-nav (atom []) replace-calls)} {})]
        (store/dispatch! store [:nav/replace! {:token        :routes.ui/home
                                               :route-params {:note/id 1}
                                               :query-params {}}])
        (testing "replaces the current route"
          (is (= [[:routes.ui/home {:note/id 1} {}]] @replace-calls)))))))

(deftest modals-test
  (tu/async done
    (async/go
      (testing "when calling :modals/create! command"
        (let [store (defacto/create {} {})]
          (store/dispatch! store [:modals/create! [:div "modal"]])

          (testing "immediately creates the modal in :init state"
            (let [[modal] (store/query store [:modals/?:modals])]
              (is (= :init (:state modal)))))

          (testing "and transitions the modal to :displayed state after the delay"
            (async/<! (async/timeout 20))
            (is (= :displayed (:state (first (store/query store [:modals/?:modals]))))))))

      (testing "when calling :modals/remove! command"
        (let [store (defacto/create {} {})]
          (store/emit! store [:modals/created 1 {:state :displayed :body [:div "x"]}])
          (store/dispatch! store [:modals/remove! 1])

          (testing "immediately hides the modal"
            (is (= :hidden (:state (first (store/query store [:modals/?:modals]))))))

          (testing "and removes the modal after the delay"
            (async/<! (async/timeout 350))
            (is (empty? (store/query store [:modals/?:modals]))))))

      (testing "when calling :modals/remove-all! command"
        (let [store (defacto/create {} {})]
          (store/emit! store [:modals/created 1 {:state :displayed :body [:div "a"]}])
          (store/emit! store [:modals/created 2 {:state :displayed :body [:div "b"]}])
          (store/dispatch! store [:modals/remove-all!])

          (testing "immediately hides all modals"
            (is (every? #(= :hidden (:state %)) (store/query store [:modals/?:modals])))
            (async/<! (async/timeout 350)))

          (testing "and removes all modals after the delay"
            (is (empty? (store/query store [:modals/?:modals]))))))

      (done))))

(deftest toasts-test
  (tu/async done
    (async/go
      (testing "when calling :toasts/succeed! command"
        (let [store (defacto/create {} {})]
          (store/dispatch! store [:toasts/succeed! {:message "it worked"}])

          (testing "creates a success toast with the given message"
            (let [[toast] (store/query store [:toasts/?:toasts])]
              (is (= :success (:level toast)))
              (is (= "it worked" (:body toast)))))))

      (testing "when calling :toasts/fail! command"
        (testing "with a map error"
          (let [store (defacto/create {} {})]
            (store/dispatch! store [:toasts/fail! {:message "oops"}])

            (testing "creates an error toast"
              (let [[toast] (store/query store [:toasts/?:toasts])]
                (is (= :error (:level toast)))
                (is (= "oops" (:body toast)))))))

        (testing "with a sequence of errors"
          (let [store (defacto/create {} {})]
            (store/dispatch! store [:toasts/fail! [{:message "e1"} {:message "e2"}]])

            (testing "creates an error toast"
              (let [[toast] (store/query store [:toasts/?:toasts])]
                (is (= :error (:level toast)))
                (is (= "e1\ne2" (:body toast)))))))

        (testing "without a usable message"
          (let [store (defacto/create {} {})]
            (store/dispatch! store [:toasts/fail! nil])

            (testing "creates an error toast with a default message"
              (let [[toast] (store/query store [:toasts/?:toasts])]
                (is (= :error (:level toast)))
                (is (= "An error occurred" (:body toast))))))))

      (testing "when calling :toasts/hide! command"
        (let [store (defacto/create {} {})
              _ (store/dispatch! store [:toasts/create! :info "msg" nil])
              {toast-id :id} (first (store/query store [:toasts/?:toasts]))]
          (store/dispatch! store [:toasts/hide! toast-id])

          (testing "immediately hides the toast"
            (is (= :hidden (:state (first (store/query store [:toasts/?:toasts]))))))

          (testing "and removes the toast after the delay"
            (async/<! (async/timeout 700))
            (is (empty? (store/query store [:toasts/?:toasts]))))))

      (testing "when calling :toasts/create! command"
        (let [store (defacto/create {} {})]
          (store/dispatch! store [:toasts/create! :success "saved" 5000])

          (testing "creates the toast with the correct attributes"
            (let [[toast] (store/query store [:toasts/?:toasts])]
              (is (= {:level :success :body "saved" :state :init :timeout 5000}
                     (dissoc toast :id)))))))

      (done))))

(deftest toasts-notes-succeed!-test
  (testing "when calling :toasts.notes/succeed!"
    (let [note-id (random-uuid)
          store (defacto/create {} {})]
      (store/dispatch! store [:toasts.notes/succeed! {:notes/id note-id}])

      (testing "creates a success toast"
        (let [[toast] (store/query store [:toasts/?:toasts])]
          (is (= :success (:level toast)))
          (is (= :init (:state toast))))))))
