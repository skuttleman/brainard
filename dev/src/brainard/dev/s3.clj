(ns brainard.dev.s3
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [integrant.core :as ig])
  (:import
    (java.io File)))

(defmulti ^:private fs-invoker :op)

(defn ^:private ensure-wkdir! [path]
  (io/make-parents (str path "/.empty")))

(defmethod fs-invoker :GetObject
  [{::keys [path] :as req}]
  (ensure-wkdir! path)
  (let [{:keys [Key]} (:request req)
        content-type-file (str path "/" Key ".content-type")
        blob-file (str path "/" Key ".blob")
        ^File file (io/file blob-file)]
    (when (.exists file)
      {:Body          (io/input-stream file)
       :ContentLength (.length file)
       :ContentType   (slurp content-type-file)})))

(defmethod fs-invoker :PutObject
  [{::keys [path] :as req}]
  (ensure-wkdir! path)
  (let [{:keys [Body Key ContentType]} (:request req)
        content-type-file (str path "/" Key ".content-type")
        blob-file (str path "/" Key ".blob")]
    (spit content-type-file ContentType)
    (io/copy Body (io/file blob-file))
    nil))

(defmethod fs-invoker :ListObjectsV2
  [{::keys [path]}]
  (ensure-wkdir! path)
  {:Contents (->> (io/file path)
                  .listFiles
                  (sequence (comp (map #(.getName %))
                                  (filter #(string/ends-with? % ".blob"))
                                  (map #(-> %
                                            (string/replace (re-pattern (format "^%s/" path)) "")
                                            (string/replace #"\..*$" "")
                                            (->> (hash-map :Key)))))))})

(defmethod fs-invoker :DeleteObjects
  [{::keys [path] :as req}]
  (ensure-wkdir! path)
  (doseq [key (->> req :request :Delete :Objects (map :Key))]
    (io/delete-file (io/file (format "%s/%s.blob" path key)))
    (io/delete-file (io/file (format "%s/%s.content-type" path key)))))

(defmethod ig/init-key :brainard/fs-invoker
  [_ {:keys [path]}]
  #(fs-invoker (assoc % ::path path)))
