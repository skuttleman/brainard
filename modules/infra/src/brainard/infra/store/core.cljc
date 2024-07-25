(ns brainard.infra.store.core
  #?(:cljs (:require-macros brainard.infra.store.core))
  (:require
    [defacto.core :as defacto]
    [defacto.forms.core :as-alias forms]
    [defacto.forms.plus :as-alias forms+]
    [defacto.resources.core :as-alias res]))

(defn dispatch! [store command]
  (defacto/dispatch! store command))

(defn emit! [store event]
  (defacto/emit! store event))

(defn subscribe [store query]
  (defacto/subscribe store query))

(defn query [store query]
  (defacto/query-responder @store query))

(defn res-sub
  ([store spec-key]
   (res-sub store spec-key nil))
  ([store spec-key opts]
   (-> store
       (dispatch! [::res/ensure! spec-key opts])
       (subscribe [::res/?:resource spec-key]))))

(defn form-sub
  ([store spec-key init]
   (form-sub store spec-key init nil))
  ([store spec-key init opts]
   (-> store
       (dispatch! [::forms/ensure! spec-key init opts])
       (subscribe [::forms/?:form spec-key]))))

(defn form+-sub
  ([store spec-key init]
   (form+-sub store spec-key init nil))
  ([store spec-key init opts]
   (-> store
       (dispatch! [::forms/ensure! spec-key init opts])
       (subscribe [::forms+/?:form+ spec-key]))))
