(ns brainard.infra.search.store
  (:require
   [brainard.api.storage.interfaces :as istorage]
   [brainard.api.utils.logger :as log]
   [brainard.infra.search.lucene :as lucene]))

(deftype NoteSearchStore [index]
  istorage/IWrite
  (write! [_ inputs]
    (doseq [input inputs]
      (case (:action input)
        :create (lucene/add! index (:doc input))
        :update (lucene/replace! index (-> input :doc :id) (:doc input))
        :delete (lucene/remove! index (:ids input)))))

  istorage/IRead
  (read [_ query]
    (case (:action query)
      :search (lucene/search index query))))

(defn ->mem-index []
  (log/info "building search store with in-memory index")
  (lucene/create-index))

(defn ->disk-index [name]
  (log/info "building search store with disk-backed index")
  (lucene/create-index (format "%s/.storage/%s/lucene"
                               (System/getProperty "user.dir")
                               name)))
