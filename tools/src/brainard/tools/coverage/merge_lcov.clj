(ns brainard.tools.coverage.merge-lcov
  (:require
    [clojure.java.io :as io]
    [clojure.java.shell :as sh]
    [clojure.string :as string]))

(def ^:private src-dirs ["src" "modules/api/src" "modules/infra/src"])
(def ^:private cljs-prefix "resources/public/js/cljs-runtime/")

(defn exit!
  ([code]
   (exit! code nil))
  ([code msg]
   (some-> msg println)
   (System/exit code)))

(defn ^:private exit-err! [code msg]
  (when-not (zero? code)
    (exit! code msg)))

(defn- sh! [& args]
  (let [{:keys [exit out err]} (apply sh/sh args)]
    (exit-err! exit (format "Command failed: %s: %s" err (string/join " " args)))
    (string/trim out)))

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

(defn- normalize-sf [sf workspace]
  (cond-> sf
    (string/starts-with? sf (str workspace "/"))
    (subs (inc (count workspace)))

    (string/starts-with? sf cljs-prefix)
    (-> (subs (count cljs-prefix)) resolve-cljs-source)))

(defn- process-lcov-file! [path workspace]
  (loop [[line & rest-lines] (string/split-lines (slurp path))
         current-lines 0
         acc []]
    (if (nil? line)
      (spit path (str (string/join "\n" acc) "\n"))
      (cond
        (string/starts-with? line "SF:")
        (let [sf (normalize-sf (subs line 3) workspace)
              n (file-line-count sf)]
          (recur rest-lines n (conj acc (str "SF:" sf))))

        (string/starts-with? line "DA:")
        (if (pos? current-lines)
          (let [lineno (-> (subs line 3) (string/split #",") first parse-long)]
            (if (<= lineno current-lines)
              (recur rest-lines current-lines (conj acc line))
              (recur rest-lines current-lines acc)))
          (recur rest-lines current-lines (conj acc line)))

        :else
        (recur rest-lines current-lines (conj acc line))))))

(defn- find-lcov-files [coverage-dir merged-dir]
  (let [merged-canonical (.getCanonicalPath (io/file merged-dir))]
    (->> (file-seq (io/file coverage-dir))
         (filter #(and (.isFile %)
                       (= "lcov.info" (.getName %))
                       (not (string/starts-with? (.getCanonicalPath %) merged-canonical))))
         (map #(.getPath %)))))

(defn- merge-lcov-files! [files merged-dir]
  (let [merged-info (str merged-dir "/merged.info")
        [first-file & rest-files] files]
    (sh! "lcov" "-a" first-file "-o" merged-info)
    (doseq [f rest-files]
      (sh! "lcov" "-a" merged-info "-a" f "-o" merged-info))))

(defn- normalize-merged! [merged-dir workspace]
  (let [merged-info (str merged-dir "/merged.info")
        lines (string/split-lines (slurp merged-info))
        normalized (map (fn [line]
                          (if (string/starts-with? line "SF:")
                            (let [sf (subs line 3)
                                  idx (string/index-of sf workspace)
                                  sf (cond-> sf
                                       idx (-> (subs (+ idx (count workspace)))
                                               (string/replace-first #"^/" "")))]
                              (str "SF:" sf))
                            line))
                        lines)]
    (spit merged-info (str (string/join "\n" normalized) "\n"))))

(defn- delete-dir! [dir]
  (doseq [f (reverse (file-seq dir))]
    (.delete f)))

(defn -main [& [coverage-dir merged-dir]]
  (let [coverage-dir (or coverage-dir "target/coverage")
        merged-dir (or merged-dir "target/coverage/merged")
        workspace (sh! "git" "rev-parse" "--show-toplevel")
        files (find-lcov-files coverage-dir merged-dir)]
    (when-not (seq files)
      (exit! 0 "No coverage files found"))
    (let [merged-f (io/file merged-dir)]
      (when (.exists merged-f) (delete-dir! merged-f))
      (.mkdirs merged-f))
    (doseq [f files] (process-lcov-file! f workspace))
    (merge-lcov-files! files merged-dir)
    (normalize-merged! merged-dir workspace)
    (io/copy (io/file merged-dir "merged.info") (io/file "merged.info"))
    (sh! "genhtml"
       "-p" workspace
       "--ignore-errors" "category"
       "-o" merged-dir
       (str merged-dir "/merged.info"))
    (println (str "Merged coverage written to " merged-dir))
    (exit! 0)))
