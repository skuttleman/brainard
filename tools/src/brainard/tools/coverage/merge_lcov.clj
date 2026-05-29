(ns brainard.tools.coverage.merge-lcov
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]))

(def ^:private src-dirs ["src" "modules/api/src" "modules/infra/src"])
(def ^:private cljs-prefix "resources/public/js/cljs-runtime/")

(defn- sh! [& args]
  (let [{:keys [exit out err]} (apply sh/sh args)]
    (when (seq out) (print out))
    (when (seq err) (binding [*out* *err*] (print err)))
    (when-not (zero? exit)
      (throw (ex-info (str "Command failed: " (str/join " " args)) {:exit exit})))
    (str/trim out)))

(defn- git-workspace-root []
  (try
    (sh! "git" "rev-parse" "--show-toplevel")
    (catch Exception _
      (System/getProperty "user.dir"))))

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
  (let [sf (if (str/starts-with? sf (str workspace "/"))
             (subs sf (inc (count workspace)))
             sf)]
    (if (str/starts-with? sf cljs-prefix)
      (resolve-cljs-source (subs sf (count cljs-prefix)))
      sf)))

(defn- process-lcov-file! [path workspace]
  (let [lines (str/split-lines (slurp path))
        result (loop [[line & rest-lines] lines
                      current-lines 0
                      acc []]
                 (if (nil? line)
                   acc
                   (cond
                     (str/starts-with? line "SF:")
                     (let [sf (normalize-sf (subs line 3) workspace)
                           n (file-line-count sf)]
                       (recur rest-lines n (conj acc (str "SF:" sf))))

                     (str/starts-with? line "DA:")
                     (if (pos? current-lines)
                       (let [lineno (-> (subs line 3) (str/split #",") first parse-long)]
                         (if (<= lineno current-lines)
                           (recur rest-lines current-lines (conj acc line))
                           (recur rest-lines current-lines acc)))
                       (recur rest-lines current-lines (conj acc line)))

                     :else
                     (recur rest-lines current-lines (conj acc line)))))]
    (spit path (str (str/join "\n" result) "\n"))))

(defn- find-lcov-files [coverage-dir merged-dir]
  (let [merged-canonical (.getCanonicalPath (io/file merged-dir))]
    (->> (file-seq (io/file coverage-dir))
         (filter #(and (.isFile %)
                       (= "lcov.info" (.getName %))
                       (not (str/starts-with? (.getCanonicalPath %) merged-canonical))))
         (map #(.getPath %)))))

(defn- merge-lcov-files! [files merged-dir]
  (let [merged-info (str merged-dir "/merged.info")
        [first-file & rest-files] files]
    (sh! "lcov" "-a" first-file "-o" merged-info)
    (doseq [f rest-files]
      (sh! "lcov" "-a" merged-info "-a" f "-o" merged-info))))

(defn- normalize-merged! [merged-dir workspace]
  (let [merged-info (str merged-dir "/merged.info")
        lines (str/split-lines (slurp merged-info))
        normalized (map (fn [line]
                          (if (str/starts-with? line "SF:")
                            (let [sf (subs line 3)
                                  idx (.indexOf sf workspace)
                                  sf (if (>= idx 0)
                                       (-> sf (subs (+ idx (count workspace))) (str/replace-first #"^/" ""))
                                       sf)]
                              (str "SF:" sf))
                            line))
                        lines)]
    (spit merged-info (str (str/join "\n" normalized) "\n"))))

(defn- delete-dir! [dir]
  (doseq [f (reverse (file-seq dir))]
    (.delete f)))

(defn -main [& [coverage-dir merged-dir]]
  (let [coverage-dir (or coverage-dir "target/coverage")
        merged-dir (or merged-dir "target/coverage/merged")
        workspace (git-workspace-root)
        files (find-lcov-files coverage-dir merged-dir)]
    (if (empty? files)
      (println "No coverage files found")
      (do
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
        (System/exit 0)))))
