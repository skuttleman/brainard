(ns brainard.infra.search.store
  (:require
    [brainard.api.storage.interfaces :as istorage]
    [brainard.api.utils.logger :as log]
    [msync.lucene :as lucene]
    [msync.lucene.analyzers :as analyzers]
    [msync.lucene.document :as ld]
    [msync.lucene.indexer :as lidx]
    [slag.utils.uuids :as uuids])
  (:import
    (org.apache.lucene.index Term)))

(def ^:private ^:const store-opts
  {:stored-fields  [:id :context :body]
   :keyword-fields [:id]
   :suggest-fields [:context]})

(defn ^:private ->result [hit]
  (when-let [id (:id (ld/document->map hit))]
    {:notes/id (uuids/->uuid id)}))

(defn ^:private add! [index doc]
  (lucene/index! index [doc] store-opts))

(defn ^:private replace! [index ^String note-id doc]
  (let [{:keys [analyzer directory]} index
        iwc (lidx/index-writer-config analyzer)]
    (with-open [iw (lidx/index-writer directory iwc)]
      (.updateDocument iw
                       (Term. "id" note-id)
                       (ld/map->document doc store-opts)))))

(defn ^:private remove! [index ^String note-id]
  (log/debug "deleting %s from index" note-id)
  (let [{:keys [analyzer directory]} index
        iwc (lidx/index-writer-config analyzer)]
    (with-open [iw (lidx/index-writer directory iwc)]
      (.deleteDocuments iw (into-array Term [(Term. "id" note-id)])))))

(defn ^:private search [index query]
  (lucene/search index
                 #{{:body (:terms query)} {:context (:terms query)}}
                 {:results-per-page 10
                  :hit->doc         ->result}))

(defn ^:private suggest [index field prefix]
  (lucene/suggest index field prefix {:hit->doc ->result}))

(deftype NoteSearchStore [index]
  istorage/IWrite
  (write! [_ inputs]
    (doseq [input inputs]
      (case (:action input)
        :create (add! index (:doc input))
        :update (replace! index (-> input :doc :id) (:doc input))
        :delete (remove! index (:id input)))))

  istorage/IRead
  (read [_ query]
    (case (:action query)
      :suggest (suggest index (:field query) (:prefix query))
      :search (search index query))))

(defn ^:private open-index [{:keys [analyzer directory]}]
  (let [iwc (lidx/index-writer-config analyzer)]
    (with-open [_ (lidx/index-writer directory iwc)])))

(defn ->mem-index []
  (log/info "building search store with in-memory index")
  (doto (lucene/create-index! :type :memory
                              :analyzer (analyzers/standard-analyzer))
    open-index))

(defn ->disk-index [name]
  (log/info "building search store with disk-backed index")
  (doto (lucene/create-index! :type :disk
                              :path (format "%s/.storage/lucene/%s"
                                            (System/getProperty "user.dir")
                                            name)
                              :analyzer (analyzers/standard-analyzer))
    open-index))
