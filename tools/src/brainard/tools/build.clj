(ns brainard.tools.build
  (:require
    [clojure.tools.build.api :as b]))

(defn clean [build-folder]
  (b/delete {:path build-folder})
  (println (format "Build folder \"%s\" removed" build-folder)))

(defn uber [_opts]
  (let [basis (b/create-basis {:project "deps.edn"})
        build-folder "target"
        jar-content "target/classes"
        main 'brainard.main
        uber-file-name "target/brainard.jar"]
    (clean build-folder)
    (b/copy-dir {:src-dirs   ["resources" "src" "modules/api/src" "modules/infra/src"]
                 :target-dir jar-content})
    (println "\nCompiling" main)
    (b/compile-clj {:basis      basis
                    :ns-compile [main]
                    :class-dir  jar-content})
    (println "\nBuilding uberjar" uber-file-name)
    (b/uber {:class-dir jar-content
             :uber-file uber-file-name
             :basis     basis
             :main      main})
    (println "\nUberjar file created:" uber-file-name)))
