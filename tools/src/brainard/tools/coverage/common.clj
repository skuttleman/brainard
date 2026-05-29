(ns brainard.tools.coverage.common
  (:require
    [clojure.java.io :as io]))

(def ^:const src-dirs ["src" "modules/api/src" "modules/infra/src"])
(def ^:const cljs-prefix "resources/public/js/cljs-runtime/")

(defn exit!
  ([code]
   (exit! code nil))
  ([code msg]
   (some-> msg println)
   (System/exit code)))

(defn exit-err! [code msg]
  (when-not (zero? code)
    (exit! code msg)))

(defn file-line-count [path]
  (let [f (io/file path)]
    (if (.exists f)
      (with-open [rdr (io/reader f)]
        (count (line-seq rdr)))
      0)))

(defn resolve-cljs-source [rel]
  (or (some (fn [dir]
              (let [candidate (str dir "/" rel)]
                (when (.exists (io/file candidate))
                  candidate)))
            src-dirs)
      rel))
