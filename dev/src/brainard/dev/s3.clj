(ns brainard.dev.s3
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [integrant.core :as ig])
  (:import
    (java.io File)))

(defmulti ^:private fs-invoker :op)

(defmethod fs-invoker :GetObject
  [req]
  (let [{:keys [Key]} (:request req)
        content-type-file (str "target/" Key ".content-type")
        blob-file (str "target/" Key ".blob")
        ^File file (io/file blob-file)]
    {:Body          (io/input-stream file)
     :ContentLength (.length file)
     :ContentType   (slurp content-type-file)}))

(defmethod fs-invoker :PutObject
  [req]
  (let [{:keys [Body Key ContentType]} (:request req)
        content-type-file (str "target/" Key ".content-type")
        blob-file (str "target/" Key ".blob")]
    (io/make-parents content-type-file)
    (spit content-type-file ContentType)
    (io/copy Body (io/file blob-file))
    nil))

(defmethod fs-invoker :ListObjectsV2
  [_]
  {:Contents (->> (file-seq (io/file "target"))
                  (remove #(.isDirectory %))
                  (map #(-> %
                            .getName
                            (string/replace #"^target/" "")
                            (string/replace #"\..*$" "")
                            (->> (hash-map :Key))))
                  distinct)})

(defmethod fs-invoker :DeleteObjects
  [req]
  (doseq [key (->> req :request :Delete :Objects (map :Key))]
    (io/delete-file (io/file (format "target/%s.blob" key)))
    (io/delete-file (io/file (format "target/%s.content-type" key)))))

(defmethod ig/init-key :brainard/fs-invoker
  [_ _]
  fs-invoker)
