(ns brainard.infra.search.lucene
  (:import
   (java.nio.file Paths)
   (org.apache.lucene.analysis.standard StandardAnalyzer)
   (org.apache.lucene.codecs.lucene104 Lucene104Codec)
   (org.apache.lucene.document Document Field$Store StringField TextField)
   (org.apache.lucene.index DirectoryReader IndexWriter IndexWriterConfig Term)
   (org.apache.lucene.search BooleanClause$Occur BooleanQuery$Builder IndexSearcher TopDocs)
   (org.apache.lucene.search.suggest.document
    Completion104PostingsFormat PrefixCompletionQuery SuggestField SuggestIndexSearcher)
   (org.apache.lucene.store ByteBuffersDirectory Directory FSDirectory)
   (org.apache.lucene.util QueryBuilder)))

(def ^:private ^:const ^String suggest-field "suggest_context")

(defn ^:private ->codec []
  (let [fmt (Completion104PostingsFormat.)]
    (proxy [Lucene104Codec] []
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
  (let [stored-fields (.storedFields searcher)]
    (mapv (fn [score-doc]
            (let [doc-id (.doc score-doc)
                  doc (.document stored-fields doc-id)
                  note-id (.get doc "id")]
              {:doc-id   doc-id
               :score    (.score score-doc)
               :notes/id (some-> note-id parse-uuid)}))
          (.scoreDocs hits))))

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

(defn remove! [index note-ids]
  (with-writer [writer index]
    (.deleteDocuments writer (into-array Term (map #(Term. "id" ^String %) note-ids)))))

(defn ^:private add-terms [qb bqb field terms]
  (doseq [term terms
          :let [q (.createBooleanQuery qb field term)]
          :when q]
    (.add bqb q BooleanClause$Occur/SHOULD)))

(defn search [{:keys [^Directory directory analyzer]} {:keys [body context]}]
  (with-open [reader (DirectoryReader/open directory)]
    (let [searcher (IndexSearcher. reader)
          qb (QueryBuilder. analyzer)
          bqb (BooleanQuery$Builder.)]
      (add-terms qb bqb "body" body)
      (add-terms qb bqb "context" context)
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
