(ns brainard.infra.search.lucene
  (:import
    (java.nio.file Paths)
    (org.apache.lucene.analysis.standard StandardAnalyzer)
    (org.apache.lucene.codecs.lucene86 Lucene86Codec)
    (org.apache.lucene.document Document Field$Store StringField TextField)
    (org.apache.lucene.index DirectoryReader IndexWriter IndexWriterConfig Term)
    (org.apache.lucene.search BooleanClause$Occur BooleanQuery$Builder IndexSearcher TopDocs)
    (org.apache.lucene.search.suggest.document
      Completion84PostingsFormat PrefixCompletionQuery SuggestField SuggestIndexSearcher)
    (org.apache.lucene.store ByteBuffersDirectory Directory FSDirectory)
    (org.apache.lucene.util QueryBuilder)))

(def ^:private ^:const ^String suggest-field "suggest_context")

(defn ^:private ->codec []
  (let [fmt (Completion84PostingsFormat.)]
    (proxy [Lucene86Codec] []
      (getPostingsFormatForField [field]
        (if (= field suggest-field)
          fmt
          (proxy-super getPostingsFormatForField field))))))

(defn ^:private ->writer-config [analyzer]
  (doto (IndexWriterConfig. analyzer)
    (.setCodec (->codec))))

(defn ^:private init-index! [{:keys [directory analyzer]}]
  (with-open [_ (IndexWriter. directory (->writer-config analyzer))]))

(defn ^:private ->document [{:keys [id context body]}]
  (doto (Document.)
    (.add (StringField. "id" (str id) Field$Store/YES))
    (.add (TextField. "context" (str context) Field$Store/NO))
    (.add (TextField. "body" (str body) Field$Store/NO))
    (cond-> (seq context) (.add (SuggestField. suggest-field context (int 1))))))

(defn ^:private hits->results [^IndexSearcher searcher ^TopDocs hits]
  (mapv (fn [score-doc]
          (let [doc (.doc searcher (.doc score-doc))]
            {:doc-id (.doc score-doc)
             :score  (.score score-doc)
             :hit    (when-let [id (.get doc "id")]
                       {:notes/id (parse-uuid id)})}))
        (.scoreDocs hits)))

(defmacro ^:private with-writer [[sym index] & body]
  `(let [{directory# :directory analyzer# :analyzer} ~index]
     (with-open [~sym (IndexWriter. directory# (->writer-config analyzer#))]
       ~@body)))

(defn add! [index doc]
  (with-writer [writer index]
    (.addDocument writer (->document doc))))

(defn replace! [index ^String note-id doc]
  (with-writer [writer index]
    (.updateDocument writer (Term. "id" note-id) (->document doc))))

(defn remove! [index ^String note-id]
  (with-writer [writer index]
    (.deleteDocuments writer (into-array Term [(Term. "id" note-id)]))))

(defn search [{:keys [^Directory directory analyzer]} terms]
  (with-open [reader (DirectoryReader/open directory)]
    (let [searcher (IndexSearcher. reader)
          qb (QueryBuilder. analyzer)
          bqb (BooleanQuery$Builder.)]
      (doseq [term terms]
        (when-let [q (.createBooleanQuery qb "body" term)]
          (.add bqb q BooleanClause$Occur/SHOULD))
        (when-let [q (.createBooleanQuery qb "context" term)]
          (.add bqb q BooleanClause$Occur/SHOULD)))
      (hits->results searcher (.search searcher (.build bqb) 10)))))

(defn suggest [{:keys [^Directory directory analyzer]} ^String prefix]
  (with-open [reader (DirectoryReader/open directory)]
    (let [searcher (SuggestIndexSearcher. reader)
          query (PrefixCompletionQuery. analyzer (Term. suggest-field prefix))]
      (hits->results searcher (.suggest searcher query 10 false)))))

(defn create-index
  ([]
   (create-index nil))
  ([path]
   (let [analyzer (StandardAnalyzer.)]
     (cond-> {:analyzer analyzer}
       path (assoc :directory (FSDirectory/open (Paths/get ^String path (into-array String []))))
       (nil? path) (assoc :directory (ByteBuffersDirectory.))
       :always (doto init-index!)))))
