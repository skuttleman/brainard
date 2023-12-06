(ns brainard.common.store.api
  (:require
    [brainard.common.utils.routing :as rte]
    [brainard.common.store.core :as store]
    [clojure.core.async :as async]
    [defacto.core :as defacto]))

(defn ^:private success? [status]
  (and (integer? status)
       (<= 200 status 299)))

(defn ^:private send-all [send-fn messages result]
  (run! send-fn (for [msg messages]
                  (conj msg result))))

(defmulti ^:private resource-spec ::spec)

(defmethod defacto/command-handler ::request!
  [{::defacto/keys [store] :services/keys [http]} [_ params] emit-cb]
  (#?(:cljs async/go :default do)
    (let [{:keys [req ok-events ok-commands err-events err-commands]} params
          response (#?(:cljs async/<! :default do) (http req))
          {:keys [data errors]} (:body response)
          payload (or errors data)
          [events commands] (if (success? (:status response))
                              [ok-events ok-commands]
                              [err-events err-commands])]
      (send-all emit-cb events payload)
      (send-all (partial store/dispatch! store) commands payload))))

(defn spec->params [spec]
  (let [params (resource-spec spec)
        url (rte/path-for (:route params) (:params params))]
    (-> {:req {:request-method (:method params)
               :url            url
               :body           (some-> (:body params) pr-str)
               :headers        {"content-type" "application/edn"}}}
        (merge (select-keys params #{:ok-commands :ok-events :err-commands :err-events}))
        (update :ok-commands into (:ok-commands spec))
        (update :ok-events into (:ok-events spec))
        (update :err-commands into (:err-commands spec))
        (update :err-events into (:err-events spec)))))

(defmethod resource-spec :api.tags/select!
  [_]
  {:route      :routes.api/tags
   :method     :get
   :ok-events  [[:resources/succeeded :api.tags/select!]]
   :err-events [[:resources/failed :api.tags/select! :remote]]})

(defmethod resource-spec :api.contexts/select!
  [_]
  {:route      :routes.api/contexts
   :method     :get
   :ok-events  [[:resources/succeeded :api.contexts/select!]]
   :err-events [[:resources/failed :api.contexts/select! :remote]]})

(defmethod resource-spec :api.notes/select!
  [{:keys [params] resource-id :resource/id}]
  {:route      :routes.api/notes
   :method     :get
   :params     params
   :ok-events  [[:resources/succeeded [:api.notes/select! resource-id]]]
   :err-events [[:resources/failed [:api.notes/select! resource-id] :remote]]})

(defmethod resource-spec :api.notes/find!
  [{:keys [params] resource-id :resource/id}]
  {:route      :routes.api/note
   :method     :get
   :params     params
   :ok-events  [[:resources/succeeded [:api.notes/find! resource-id]]]
   :err-events [[:resources/failed [:api.notes/find! resource-id] :remote]]})

(defmethod resource-spec :api.notes/create!
  [{resource-id :resource/id :keys [body]}]
  {:route        :routes.api/notes
   :method       :post
   :body         body
   :ok-events    [[:resources/note-saved]]
   :ok-commands  [[:toasts/succeed! {:message "note created"}]]
   :err-events   [[:resources/failed [:api.notes/create! resource-id] :remote]]
   :err-commands [[:toasts/fail!]]})

(defmethod resource-spec :api.notes/update!
  [{resource-id :resource/id :keys [body params]}]
  {:route        :routes.api/note
   :params       params
   :method       :patch
   :body         body
   :ok-events    [[:resources/note-saved]]
   :ok-commands  [[:toasts/succeed! {:message "note updated"}]]
   :err-events   [[:resources/failed [:api.notes/update! resource-id] :remote]]
   :err-commands [[:toasts/fail!]]})
