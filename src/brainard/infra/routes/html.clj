(ns brainard.infra.routes.html
  (:require
    [brainard.common.utils.edn :as edn]
    [hiccup.core :as hiccup]))

(defn render
  "Renders an HTML template"
  [template]
  (->> template
       edn/resource
       hiccup/html
       (str "<!doctype html>")))
