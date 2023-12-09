(ns brainard.common.store.flows-test
  (:require
    [brainard.common.forms.core :as forms]
    [brainard.common.resources.api :as store.api]
    [brainard.common.store.core :as store]
    [brainard.common.resources.specs :as-alias rspecs]
    [brainard.common.stubs.nav :as nav]
    [brainard.common.utils.routing :as rte]
    [brainard.common.utils.uuids :as uuids]
    [clojure.test :refer [deftest is testing]]
    [defacto.core :as defacto]
    brainard.common.store.commands
    brainard.common.store.events
    brainard.common.store.queries))

(deftest forms-test
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

(deftest resources-test
  (let [method (::store.api/request! (methods defacto/command-handler))
        calls (atom [])
        handler (fn [_ [_ params] _]
                  (swap! calls conj params))
        store (store/create)
        note-id (uuids/random)]
    (testing "when ensuring the resource exists"
      (try
        (#?(:cljs -add-method :default .addMethod) defacto/command-handler
                                                   ::store.api/request!
                                                   handler)

        (testing "and when the resource does exist"
          (reset! calls [])
          (store/dispatch! store [:resources/ensure! [::rspecs/notes#find note-id]])

          (testing "submits the resource"
            (let [[input] @calls]
              (is (= {:req        {:request-method :get
                                   :url            (str "/api/notes/" note-id)}
                      :ok-events  [[:resources/succeeded [::rspecs/notes#find note-id]]]
                      :err-events [[:resources/failed [::rspecs/notes#find note-id] :remote]]}
                     (update input :req select-keys #{:request-method :url}))))))

        (testing "and when the resource exists"
          (reset! calls [])
          (store/dispatch! store [:resources/ensure! [::rspecs/notes#find note-id]])

          (testing "does not submit the resource"
            (is (empty? @calls))))

        (testing "when submitting an existing resource"
          (reset! calls [])
          (store/dispatch! store [:resources/submit! [::rspecs/notes#find note-id]])

          (testing "submits the resource"
            (is (not (empty? @calls)))))
        (finally
          (#?(:cljs -add-method :default .addMethod) defacto/command-handler
                                                     ::store.api/request!
                                                     method))))))

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
