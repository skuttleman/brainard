(ns brainard.notes.infra.search
  (:require [brainard.api.storage.interfaces :as istorage]
            [brainard.notes.api.core :as-alias api.notes]))

(defmethod istorage/->input ::api.notes/search
  [{:notes/keys [body context]}]
  {:action  :search
   :body    body
   :context context})

(defmethod istorage/->input ::api.notes/search-suggest
  [{:notes/keys [query]}]
  {:action :suggest
   :field  :context
   :prefix query})

(defmethod istorage/->input ::api.notes/search-create!
  [{:notes/keys [id body context]}]
  [{:action :create
    :doc    {:id      (str id)
             :body    body
             :context context}}])

(defmethod istorage/->input ::api.notes/search-update!
  [{:notes/keys [id body context]}]
  [{:action :update
    :doc    {:id      (str id)
             :body    body
             :context context}}])

(defmethod istorage/->input ::api.notes/search-delete!
  [{:notes/keys [id]}]
  [{:action :delete
    :id     (str id)}])
