(ns brainard.attachments.infra.multipart-params
  (:require
    [brainard :as-alias b]
    [brainard.api.validations :as-alias valid]
    [ring.util.request :as req])
  (:import
    (java.io InputStream)
    (org.apache.commons.fileupload FileItemIterator FileItemStream FileUpload UploadContext)
    (org.apache.commons.fileupload.util LimitedInputStream)))

(defn ^:private multipart-form? [request]
  (= (req/content-type request) "multipart/form-data"))

(defn ^:private ->UploadContext [request encoding]
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

(defn ^:private ->LimitedInputStream ^InputStream [^InputStream is ^long file-limit-bytes]
  (proxy [LimitedInputStream] [is file-limit-bytes]
    (raiseError [max _count]
      (.close this)
      (throw (ex-info "File upload too big"
                      {::valid/type ::valid/upload-too-big
                       :details     {:max-size max}})))))

(defn ^:private file-item-iterator-seq [^FileItemIterator it]
  (lazy-seq
    (when (.hasNext it)
      (cons (.next it) (file-item-iterator-seq it)))))

(defn ^:private file-item-seq [context]
  (let [upload (FileUpload.)]
    (file-item-iterator-seq
      (.getItemIterator ^FileUpload upload context))))

(defn ^:private parse-file-item [file-limit-bytes ^FileItemStream item]
  {:filename     (.getName item)
   :content-type (.getContentType item)
   :stream       (->LimitedInputStream (.openStream item) file-limit-bytes)})

(defn ^:private parse-multipart-params [{::b/keys [file-limit-bytes] :as request} fallback-encoding]
  {:files (->> (->UploadContext request fallback-encoding)
               file-item-seq
               (map (partial parse-file-item file-limit-bytes)))})

(defn multipart-params-request [request]
  (let [req-encoding (or (req/character-encoding request) "UTF-8")
        params (if (multipart-form? request)
                 (parse-multipart-params request req-encoding)
                 {})]
    (assoc request :multipart-params params)))

(defn wrap-multipart-params [handler]
  (comp handler multipart-params-request))
