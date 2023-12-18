(ns brainard.build
  (:require
    [clojure.tools.build.api :as b]))

(defn clean [{:keys [build-folder]}]
  (b/delete {:path "target"})
  (println (format "Build folder \"%s\" removed" build-folder)))

(defn uber [{:keys [jar-content basis main uber-file-name] :as attrs}]
  (clean attrs)

  (b/copy-dir {:src-dirs   ["resources" "src"]
               :target-dir jar-content})

  (b/compile-clj {:basis     basis
                  :src-dirs  ["src"]
                  :class-dir jar-content})

  (b/uber {:class-dir jar-content
           :uber-file uber-file-name
           :basis     basis
           :main      main})

  (println (format "Uber file created: \"%s\"" uber-file-name)))

(defn default-uber []
  (uber {:basis          (b/create-basis {:project "deps.edn"})
         :build-folder   "target"
         :jar-content    "target/classes"
         :main           'brainard.core
         :uber-file-name "target/brainard.jar"}))
