(ns brainard.workspace.infra.store
  (:require
    [brainard.api.utils.maps :as maps]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.resources.system :as-alias res.sys]
    [brainard.workspace.api.core :as api.work]
    [clojure.core.async :as async]
    [defacto.resources.core :as-alias res]
    [integrant.core :as ig]
    [whet.core :as w]
    [whet.interfaces :as iwhet]))

(defmulti ^:private connect-api (fn [{[_ type] ::w/type} _] type))

(defn ^:private with-api [sys k f & args]
  (let [api (val (ig/find-derived-1 sys k))]
    (apply f api args)))

(defmethod connect-api ::specs/workspace#fetch
  [_ sys]
  (with-api sys :brainard/workspace-api api.work/get-workspace))

(defmethod connect-api ::specs/workspace#create!
  [params sys]
  (with-api sys :brainard/workspace-api api.work/create! params)
  nil)

(defmethod connect-api ::specs/workspace#delete!
  [params sys]
  (with-api sys :brainard/workspace-api api.work/delete! (:workspace-nodes/id params))
  nil)

(defmethod connect-api ::specs/workspace#move!
  [{:workspace-nodes/keys [id new-parent-id old-parent-id]} sys]
  (with-api sys :brainard/workspace-api api.work/move! old-parent-id new-parent-id id)
  nil)

(defmethod connect-api ::specs/workspace#detach!
  [{:workspace-nodes/keys [id]} sys]
  (with-api sys :brainard/workspace-api api.work/detach! id)
  nil)

(defmethod iwhet/handle-request ::specs/local
  [_ {:brainard/keys [sys]} params]
  (async/go
    (try
      [::res/ok (connect-api params sys)]
      (catch #?(:cljs :default :default Throwable) ex
        [::res/err (maps/assoc-defaults (ex-data ex) :message (ex-message ex))]))))

(defmethod ig/init-key ::res.sys/const
  [_ cfg]
  cfg)
