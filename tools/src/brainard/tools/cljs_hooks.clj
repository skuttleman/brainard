(ns brainard.tools.cljs-hooks
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as string]))

(defn- js-escape [s]
  (-> s
      (string/replace "\\" "\\\\")
      (string/replace "\"" "\\\"")
      (string/replace "\r" "\\r")
      (string/replace "\n" "\\n")))

(defn instrument-code
  {:shadow.build/stage :flush}
  [state]
  (let [cljs-runtime-dir "resources/public/js/cljs-runtime"
        main-js-path "resources/public/js/main.js"
        nyc "./node_modules/.bin/nyc"
        {:keys [exit err]} (sh nyc "instrument" "--in-place" cljs-runtime-dir)]
    (if-not (zero? exit)
      (binding [*out* *err*]
        (println "Instrumentation failed:" err))
      (let [prefix-len (count "SHADOW_ENV.evalLoad(\"")]
        (->> (string/split-lines (slurp main-js-path))
             (map (fn [line]
                    (if-not (string/starts-with? line "SHADOW_ENV.evalLoad(\"brainard.")
                      line
                      (let [fname-end (string/index-of line "\"" prefix-len)
                            filename (subs line prefix-len fname-end)
                            instrumented-file (io/file cljs-runtime-dir filename)]
                        (if (or (string/starts-with? filename "brainard.test.")
                                (not (.exists instrumented-file)))
                          line
                          (let [after-fname (str "SHADOW_ENV.evalLoad(\"" filename "\", ")
                                cacheable-start (count after-fname)
                                comma-pos (string/index-of line ", " cacheable-start)
                                cacheable (subs line cacheable-start comma-pos)
                                instrumented-code (slurp instrumented-file)]
                            (str after-fname cacheable ", \"" (js-escape instrumented-code) "\");")))))))
             (string/join "\n")
             (spit main-js-path))
        (println "Instrumentation complete!"))))
  state)
