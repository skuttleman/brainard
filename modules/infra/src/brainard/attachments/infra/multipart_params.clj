(ns brainard.attachments.infra.multipart-params
  (:require
    [ring.util.request :as req])
  (:import
    (org.apache.commons.fileupload FileItemIterator FileItemStream FileUpload UploadContext)))

(defn ^:private multipart-form? [request]
  (= (req/content-type request) "multipart/form-data"))

(defn ^:private request-context [request encoding]
  (reify UploadContext
    (getContentType [_]
      (get-in request [:headers "content-type"]))
    (getContentLength [_]
      (or (req/content-length request) -1))
    (contentLength [_]
      (or (req/content-length request) -1))
    (getCharacterEncoding [_]
      encoding)
    (getInputStream [_]
      (:body request))))

(defn ^:private file-item-iterator-seq [^FileItemIterator it]
  (lazy-seq
    (when (.hasNext it)
      (cons (.next it) (file-item-iterator-seq it)))))

(defn ^:private file-item-seq [context]
  (let [upload (FileUpload.)]
    (file-item-iterator-seq
      (.getItemIterator ^FileUpload upload context))))

(defn ^:private parse-file-item [^FileItemStream item]
  {:filename     (.getName item)
   :content-type (.getContentType item)
   :stream       (.openStream item)})

(defn ^:private parse-multipart-params [request fallback-encoding]
  {:files (->> (request-context request fallback-encoding)
               file-item-seq
               (map parse-file-item))})

(defn multipart-params-request [request]
  (let [req-encoding (or (req/character-encoding request) "UTF-8")
        params (if (multipart-form? request)
                 (parse-multipart-params request req-encoding)
                 {})]
    (assoc request :multipart-params params)))

(defn wrap-multipart-params [handler]
  (comp handler multipart-params-request))
