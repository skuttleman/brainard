(ns brainard.infra.utils.routing
  "Tokenized HTTP routing table for use with [[bidi.bidi]]"
  (:require
    [bidi.bidi :as bidi]
    [brainard.api.utils.colls :as colls]
    [brainard.api.utils.keywords :as kw]
    [brainard.api.utils.uuids :as uuids]
    [clojure.string :as string])
  #?(:clj
     (:import
       (java.net URLEncoder URLDecoder))))

(def ^:private token->coercers
  {:routes.api/note     {:notes/id uuids/->uuid}
   :routes.ui/note      {:notes/id uuids/->uuid}
   :routes.api/schedule {:schedules/id uuids/->uuid}})

(def ^:private api-routes
  ["/api" [["/notes" [["" :routes.api/notes]
                      [["/" [uuids/regex :notes/id]] :routes.api/note]
                      ["/scheduled" :routes.api/notes?scheduled]]]
           ["/schedules" [["" :routes.api/schedules]
                          [["/" [uuids/regex :schedules/id]] :routes.api/schedule]]]
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
       ["/buzz" :routes.ui/buzz]
       ["/search" :routes.ui/search]
       [["/notes/" [uuids/regex :notes/id]] :routes.ui/note]
       [true :routes.ui/not-found]]])

(def ^:private all-routes
  ["" [api-routes resource-routes ui-routes]])

(defn ^:private coerce-params [params coercers]
  (reduce (fn [params [k coercer]]
            (cond-> params
              (contains? params k)
              (update k coercer)))
          params
          coercers))

(defn ^:private encode [s]
  #?(:cljs    (js/encodeURIComponent s)
     :default (URLEncoder/encode ^String s "UTF8")))

(defn ^:private decode [s]
  #?(:cljs    (js/decodeURIComponent s)
     :default (URLDecoder/decode ^String s "UTF8")))

(defn ->query-string
  "Converts a map of query-params into a query-string.

  (->query-string {:a 1 :b [:foo :bar]})
  ;; => \"a=1&b=foo&b=bar\""
  [params]
  (some->> params
           (mapcat (fn [[k v]]
                     (when (some? v)
                       (map (fn [v']
                              (cond-> (name k)
                                (not (true? v'))
                                (str "=" (encode (str (cond-> v' (keyword? v') kw/str))))))
                            (cond-> v (not (coll? v)) vector)))))
           seq
           (string/join "&")))

(defn ->query-params
  "Parses a query-string into a map of params. Param values will be a string or set of strings.

  (->query-params \"a=1&b=foo&b=bar\")
  ;; => {:a \"1\" :b #{\"foo\" \"bar\"}}"
  [query]
  (when (seq query)
    (reduce (fn [params pair]
              (let [[k v] (string/split pair #"=")
                    k (keyword k)
                    v (if (re-find #"=" pair) (decode (str v)) true)]
                (if (contains? params k)
                  (assoc params k (conj (colls/wrap-set (get params k)) v))
                  (assoc params k v))))
            {}
            (string/split query #"&"))))

(defn ^:private with-qp [uri query-params]
  (let [query (->query-string query-params)]
    (cond-> uri
      query (str "?" query))))

(defn path-for
  "Produces a path from a route handle and optional params."
  ([token]
   (path-for token nil))
  ([token params]
   (let [route-info (apply bidi/path-for all-routes token (flatten (seq params)))]
     (with-qp route-info (:query-params params)))))

(defn match
  "Matches a route uri and parses route info."
  [uri]
  (let [[path query-string] (string/split uri #"\?")
        anchor #?(:cljs    (some-> js/document.location.hash not-empty (subs 1))
                  :default nil)
        {:keys [handler route-params]} (bidi/match-route all-routes path)
        coercers (token->coercers handler)]
    {:token        handler
     :uri          uri
     :anchor       anchor
     :route-params (coerce-params route-params coercers)
     :query-params (->query-params query-string)}))
