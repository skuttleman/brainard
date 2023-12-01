(ns brainard.common.services.navigation.core
  (:require
    #?(:cljs [pushy.core :as pushy])
    [bidi.bidi :as bidi]
    [brainard.common.services.navigation.routing :as nav.route]
    [brainard.common.utils.colls :as colls]
    [brainard.common.utils.keywords :as kw]
    [brainard.common.services.store.core :as store]
    [clojure.string :as string]))

(defn ^:private nav-dispatch [route]
  (store/dispatch [:routing/navigate route]))

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

(defn match
  "Matches a route uri and parses route info."
  [path]
  (let [[path query-string] (string/split path #"\?")
        route-info (bidi/match-route nav.route/all path)
        coercers (nav.route/handler->coercers (:handler route-info))]
    (-> route-info
        (update :route-params coerce-params coercers)
        (assoc :query-params (->query-params query-string)))))

(defonce ^:private link
  #?(:cljs    (doto (pushy/pushy nav-dispatch match)
                pushy/start!)
     :default nil))

(defn ^:private with-qp [uri query-params]
  (let [query (->query-string query-params)]
    (cond-> uri
      query (str "?" query))))

(defn path-for
  "Produces a path from a route handle and optional params."
  ([handle]
   (path-for handle nil))
  ([handle params]
   (apply bidi/path-for nav.route/all handle (flatten (seq params)))))

(defn navigate-uri!
  "Navigates to a new uri with history."
  [uri]
  #?(:cljs
     (pushy/set-token! link uri)))

(defn navigate!
  "Generates a routing uri and navigates via [[navigate-uri!]]."
  ([handle]
   (navigate! handle nil))
  ([handle params]
   (navigate-uri! (with-qp (path-for handle params) (:query-params params)))))

(defn replace-uri!
  "Replaces the current route with a new uri, removing the current route from browser history."
  [uri]
  #?(:cljs
     (pushy/replace-token! link uri)))

(defn replace!
  "Generates a routing uri and navigates via [[replace-uri!]]."
  ([handle]
   (replace! handle nil))
  ([handle params]
   (replace-uri! (with-qp (path-for handle params) (:query-params params)))))
