(ns brainard.infra.routes.interfaces
  (:require
    [whet.core :as-alias w]))

(def routing-hierarchy
  (-> (make-hierarchy)
      (derive :get :any)
      (derive :post :any)
      (derive :put :any)
      (derive :patch :any)
      (derive :delete :any)

      (derive :routes.api/notes :routes/api)
      (derive :routes.api/note :routes/api)
      (derive :routes.api/notes?pinned :routes/api)
      (derive :routes.api/notes?scheduled :routes/api)
      (derive :routes.api/schedules :routes/api)
      (derive :routes.api/schedule :routes/api)
      (derive :routes.api/tags :routes/api)
      (derive :routes.api/contexts :routes/api)

      (derive :routes.resources/js :routes.resources/asset)
      (derive :routes.resources/css :routes.resources/asset)
      (derive :routes.resources/img :routes.resources/asset)
      (derive :routes.ui/buzz :routes/ui)
      (derive :routes.ui/main :routes/ui)
      (derive :routes.ui/search :routes/ui)
      (derive :routes.ui/note :routes/ui)
      (derive :routes.ui/pinned :routes/ui)
      (derive :routes.ui/dev :routes/ui)
      (derive :routes.ui/not-found :routes/ui)))

(def route->handler
  {[:get :routes.api/notes?scheduled] :api.notes/relevant
   [:get :routes.api/notes?pinned]    :api.notes/select
   [:get :routes.api/notes]           :api.notes/select
   [:post :routes.api/notes]          :api.notes/create!
   [:get :routes.api/note]            :api.notes/fetch
   [:patch :routes.api/note]          :api.notes/update!
   [:delete :routes.api/note]         :api.notes/delete!
   [:get :routes.api/tags]            :api.tags/select
   [:get :routes.api/contexts]        :api.contexts/select
   [:post :routes.api/schedules]      :api.schedules/create!
   [:delete :routes.api/schedule]     :api.schedules/delete!})

(defn router [{:keys [request-method] ::w/keys [route]}]
  [request-method (:token route)])

(defmulti ^{:arglists '([req])} handler
          "Defines an HTTP route handler. Dispatch value is a tuple of `[request-method handler-token]`."
          router
          :hierarchy
          #'routing-hierarchy)

(defmulti ^{:arglists '([req])} req->input
          "Defines an HTTP route coercer which gathers relevant data for the request. Defaults to the request :body."
          router
          :hierarchy
          #'routing-hierarchy)
