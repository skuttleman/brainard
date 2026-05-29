(ns brainard.tools.coverage.normalize-js
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]))

(def ^:private src-dirs ["src" "modules/api/src" "modules/infra/src"])
(def ^:private cljs-prefix "resources/public/js/cljs-runtime/")

(defn- file-line-count [path]
  (let [f (io/file path)]
    (if (.exists f)
      (with-open [rdr (io/reader f)]
        (count (line-seq rdr)))
      0)))

(defn- resolve-cljs-source [rel]
  (or (some (fn [dir]
              (let [candidate (str dir "/" rel)]
                (when (.exists (io/file candidate)) candidate)))
            src-dirs)
      rel))

(defn- process-lines [lines]
  (loop [[line & rest-lines] lines
         current-lines 0
         acc []]
    (if (nil? line)
      acc
      (cond
        (str/starts-with? line "SF:")
        (let [path (str/replace-first (subs line 3) cljs-prefix "")
              resolved (resolve-cljs-source path)
              n (file-line-count resolved)]
          (recur rest-lines n (conj acc (str "SF:" resolved))))

        (and (str/starts-with? line "DA:") (pos? current-lines))
        (let [lineno (-> (subs line 3) (str/split #",") first parse-long)]
          (if (<= lineno current-lines)
            (recur rest-lines current-lines (conj acc line))
            (recur rest-lines current-lines acc)))

        :else
        (recur rest-lines current-lines (conj acc line))))))

(defn -main [& _]
  (let [f (io/file "target/coverage/js/lcov.info")]
    (if-not (.exists f)
      (println "No JS coverage report generated (skipping)")
      (do
        (println "Normalizing JS coverage source paths...")
        (spit f (str (str/join "\n" (process-lines (str/split-lines (slurp f)))) "\n"))
        (println "JS coverage paths normalized")))))
