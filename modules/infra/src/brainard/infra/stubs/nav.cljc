(ns brainard.infra.stubs.nav
  (:require
    [brainard.infra.utils.routing :as rte]))

(defprotocol INavigate
  (-set! [this uri])
  (-replace! [this uri]))

(defn navigate!
  "Navigates with history to a URI (string) or `token` (keyword) with option `params`."
  ([nav token-or-uri]
   (if (string? token-or-uri)
     (-set! nav token-or-uri)
     (navigate! nav token-or-uri nil)))
  ([nav token params]
   (-set! nav (rte/path-for token params))))

(defn replace!
  "Takes a string or routing token and optional params"
  ([nav token-or-uri]
   (if (string? token-or-uri)
     (-replace! nav token-or-uri)
     (replace! nav token-or-uri nil)))
  ([nav token params]
   (-replace! nav (rte/path-for token params))))
