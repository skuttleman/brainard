(ns brainard.common.utils.routing
  "Tokenized HTTP routing table for use with [[bidi.bidi]]"
  (:require
    [bidi.bidi :as bidi]
    [brainard.common.utils.colls :as colls]
    [brainard.common.utils.keywords :as kw]
    [brainard.common.utils.uuids :as uuids]
    [clojure.string :as string]))

(def ^:private api-routes
  ["/api" [["/notes" [["" :routes.api/notes]
                      [["/" [uuids/regex :notes/id]] :routes.api/note]]]
           ["/tags" :routes.api/tags]
           ["/contexts" :routes.api/contexts]
           [true :routes.api/not-found]]])

(def ^:private resource-routes
  ["" [["/js/" [[true :routes.resources/js]]]
       ["/css/" [[true :routes.resources/css]]]
       ["/img/" [[true :routes.resources/img]]]
       ["/favicon.ico" :routes.resources/not-found]]])

(def ^:private ui-routes
  ["" [["/" :routes.ui/home]
       ["/search" :routes.ui/search]
       [["/notes/" [uuids/regex :notes/id]] :routes.ui/note]
       [true :routes.ui/not-found]]])

(def ^:private all-routes
  ["" [api-routes resource-routes ui-routes]])

(def ^:private handler->coercers
  {:routes.api/note {:notes/id uuids/->uuid}
   :routes.ui/note  {:notes/id uuids/->uuid}})

(defn ^:private coerce-params [params coercers]
  (reduce (fn [params [k coercer]]
            (cond-> params
              (contains? params k)
              (update k coercer)))
          params
          coercers))

(defn ->query-string
  "Converts a map of query-params into a query-string.

  (->query-string {:a 1 :b [:foo :bar]})
  ;; => \"a=1&b=foo&b=bar\""
  [params]
  (some->> params
           (mapcat (fn [[k v]]
                     (when (some? v)
                       (map (fn [v']
                              (str (name k) "=" (cond-> v' (keyword? v') kw/str)))
                            (cond-> v (not (coll? v)) vector)))))
           seq
           (string/join "&")))

(defn ^:private with-qp [uri query-params]
  (let [query (->query-string query-params)]
    (cond-> uri
      query (str "?" query))))

(defn ->query-params
  "Parses a query-string into a map of params. Param values will be a string or set of strings.

  (->query-params \"a=1&b=foo&b=bar\")
  ;; => {:a \"1\" :b #{\"foo\" \"bar\"}}"
  [query]
  (when (seq query)
    (reduce (fn [params pair]
              (let [[k v] (string/split pair #"=")
                    k (keyword k)
                    v (if (re-find #"=" pair) (str v) true)]
                (if (contains? params k)
                  (assoc params k (conj (colls/wrap-set (get params k)) v))
                  (assoc params k v))))
            {}
            (string/split query #"&"))))

(defn path-for
  "Produces a path from a route handle and optional params."
  ([handle]
   (path-for handle nil))
  ([handle params]
   (let [route-info (apply bidi/path-for all-routes handle (flatten (seq params)))]
     (with-qp route-info (:query-params params)))))

(defn match
  "Matches a route uri and parses route info."
  [path]
  (let [[path query-string] (string/split path #"\?")
        route-info (bidi/match-route all-routes path)
        coercers (handler->coercers (:handler route-info))]
    (-> route-info
        (update :route-params coerce-params coercers)
        (assoc :query-params (->query-params query-string)))))