(ns brainard.workspace.infra.store
  (:require
    [brainard.infra.store.specs :as-alias specs]
    [brainard.resources.system :as-alias res.sys]
    [brainard.workspace.api.core :as api.work]
    [clojure.core.async :as async]
    [defacto.forms.core :as forms]
    [defacto.forms.plus :as-alias forms+]
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
  (with-api sys :brainard/workspace-api api.work/create! (::forms/data params)))

(defmethod iwhet/handle-request ::specs/local
  [_ {:brainard/keys [sys]} params]
  (async/go
    (try
      [::res/ok (connect-api params sys)]
      (catch #?(:cljs :default :default Throwable) ex
        [::res/err (ex-data ex)]))))

(defmethod ig/init-key ::res.sys/const
  [_ cfg]
  cfg)
