(ns brainard.tools.coverage.merge-lcov
  (:require
    [brainard.tools.coverage.common :as cov.common]
    [clojure.java.io :as io]
    [clojure.java.shell :as sh]
    [clojure.string :as string]))

(defn ^:private sh! [& args]
  (let [{:keys [exit out err]} (apply sh/sh args)]
    (cov.common/exit-err! exit (format "Command failed: %s: %s" err (string/join " " args)))
    (string/trim out)))

(defn ^:private normalize-sf [sf workspace]
  (let [sf (cond-> sf
             (string/starts-with? sf (str workspace "/"))
             (subs (inc (count workspace)))

             (string/starts-with? sf cov.common/cljs-prefix)
             (-> (subs (count cov.common/cljs-prefix)) cov.common/resolve-cljs-source))]
    (cond->> sf
      (not (string/starts-with? sf "/"))
      (str workspace "/"))))

(defn ^:private process-lcov-file! [path workspace]
  (loop [[line & rest-lines] (string/split-lines (slurp path))
         current-lines 0
         acc []]
    (if (nil? line)
      (spit path (str (string/join "\n" acc) "\n"))
      (cond
        (string/starts-with? line "SF:")
        (let [sf (normalize-sf (subs line 3) workspace)
              n (cov.common/file-line-count sf)]
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

(defn ^:private find-lcov-files [coverage-dir merged-dir]
  (let [merged-canonical (.getCanonicalPath (io/file merged-dir))]
    (->> (file-seq (io/file coverage-dir))
         (filter #(and (.isFile %)
                       (= "lcov.info" (.getName %))
                       (not (string/starts-with? (.getCanonicalPath %) merged-canonical))))
         (map #(.getPath %)))))

(defn ^:private merge-lcov-files! [files merged-dir]
  (let [merged-info (str merged-dir "/merged.info")
        [first-file & rest-files] files]
    (sh! "lcov" "-a" first-file "-o" merged-info)
    (doseq [f rest-files]
      (sh! "lcov" "-a" merged-info "-a" f "-o" merged-info))))

(defn ^:private normalize-merged! [merged-dir workspace]
  (let [merged-info (str merged-dir "/merged.info")
        lines (string/split-lines (slurp merged-info))
        normalized (map (fn [line]
                          (if (string/starts-with? line "SF:")
                            (let [sf (subs line 3)
                                  sf (cond-> sf
                                       (not (string/starts-with? sf "/"))
                                       (str workspace "/"))]
                              (str "SF:" sf))
                            line))
                        lines)]
    (spit merged-info (str (string/join "\n" normalized) "\n"))))

(defn ^:private delete-dir! [dir]
  (doseq [f (reverse (file-seq dir))]
    (.delete f)))

(defn -main [& [coverage-dir merged-dir]]
  (let [coverage-dir (or coverage-dir "target/coverage")
        merged-dir (or merged-dir "target/coverage/merged")
        workspace (sh! "git" "rev-parse" "--show-toplevel")
        files (find-lcov-files coverage-dir merged-dir)]
    (when-not (seq files)
      (cov.common/exit! 0 "No coverage files found"))
    (let [merged-f (io/file merged-dir)]
      (when (.exists merged-f)
        (delete-dir! merged-f))
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
    (cov.common/exit! 0 (str "Merged coverage written to " merged-dir))))
