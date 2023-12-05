(ns brainard.common.store.api
  (:require
    [brainard.common.utils.routing :as rte]
    [brainard.common.store.core :as store]
    [clojure.core.async :as async]))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 299)))

(defmulti ^:private resource-spec ::spec)

(defn request!
  "Sends HTTP request and returns a core.async channel which dispatches
   `on-success-n` or `on-error-n` events with the corresponding result or errors."
  [store request-fn spec]
  (#?(:cljs async/go :default do)
    (let [params (resource-spec spec)
          url (rte/path-for (:route params) (:params params))
          request {:request-method (:method params)
                   :url            url
                   :body           (some-> (:body params) pr-str)
                   :headers        {"content-type" "application/edn"}}
          response (#?(:cljs async/<! :default do) (request-fn request))
          {:keys [data errors]} (:body response)]
      (if (success? (:status response))
        (run! (comp (partial store/dispatch! store) #(conj % data))
              (concat (:on-success-n params) (:on-success-n spec)))
        (run! (comp (partial store/dispatch! store) #(conj % errors))
              (concat (:on-error-n params) (:on-error-n spec))))
      nil)))

(defmethod resource-spec :api.tags/select!
  [_]
  {:route        :routes.api/tags
   :method       :get
   :on-success-n [[::store/emit! [:resources/succeeded :api.tags/select!]]]
   :on-error-n   [[::store/emit! [:resources/failed :api.tags/select! :remote]]]})

(defmethod resource-spec :api.contexts/select!
  [_]
  {:route        :routes.api/contexts
   :method       :get
   :on-success-n [[::store/emit! [:resources/succeeded :api.contexts/select!]]]
   :on-error-n   [[::store/emit! [:resources/failed :api.contexts/select! :remote]]]})

(defmethod resource-spec :api.notes/select!
  [{:keys [params] resource-id :resource/id}]
  {:route        :routes.api/notes
   :method       :get
   :params       params
   :on-success-n [[::store/emit! [:resources/succeeded [:api.notes/select! resource-id]]]]
   :on-error-n   [[::store/emit! [:resources/failed [:api.notes/select! resource-id] :remote]]]})

(defmethod resource-spec :api.notes/find!
  [{:keys [params] resource-id :resource/id}]
  {:route        :routes.api/note
   :method       :get
   :params       params
   :on-success-n [[::store/emit! [:resources/succeeded [:api.notes/find! resource-id]]]]
   :on-error-n   [[::store/emit! [:resources/failed [:api.notes/find! resource-id] :remote]]]})

(defmethod resource-spec :api.notes/create!
  [{resource-id :resource/id :keys [body]}]
  {:route        :routes.api/notes
   :method       :post
   :body         body
   :on-success-n [[:toasts/succeed! {:message "note created"}]
                  [::store/emit! [:resources/note-saved]]]
   :on-error-n   [[:toasts/fail!]
                  [::store/emit! [:resources/failed [:api.notes/create! resource-id] :remote]]]})

(defmethod resource-spec :api.notes/update!
  [{resource-id :resource/id :keys [body params]}]
  {:route        :routes.api/note
   :params       params
   :method       :patch
   :body         body
   :on-success-n [[:toasts/succeed! {:message "note updated"}]
                  [::store/emit! [:resources/note-saved]]]
   :on-error-n   [[:toasts/fail!]
                  [::store/emit! [:resources/failed [:api.notes/update! resource-id] :remote]]]})
