(ns brainard.tools.coverage.normalize-js
  (:require
    [brainard.tools.coverage.common :as cov.common]
    [clojure.java.io :as io]
    [clojure.string :as string]))

(defn ^:private process-lines [lines]
  (loop [[line & rest-lines] lines
         current-lines 0
         acc []]
    (if (nil? line)
      acc
      (cond
        (string/starts-with? line "SF:")
        (let [path (string/replace-first (subs line 3) cov.common/cljs-prefix "")
              resolved (cov.common/resolve-cljs-source path)
              n (cov.common/file-line-count resolved)]
          (recur rest-lines n (conj acc (str "SF:" resolved))))

        (and (string/starts-with? line "DA:") (pos? current-lines))
        (let [lineno (-> (subs line 3) (string/split #",") first parse-long)]
          (if (<= lineno current-lines)
            (recur rest-lines current-lines (conj acc line))
            (recur rest-lines current-lines acc)))

        :else
        (recur rest-lines current-lines (conj acc line))))))

(defn -main [& _]
  (let [f (io/file "target/coverage-js/lcov.info")]
    (if-not (.exists f)
      (println "No JS coverage report generated (skipping)")
      (do
        (println "Normalizing JS coverage source paths...")
        (spit f (str (string/join "\n" (process-lines (string/split-lines (slurp f)))) "\n"))
        (println "JS coverage paths normalized")))))
