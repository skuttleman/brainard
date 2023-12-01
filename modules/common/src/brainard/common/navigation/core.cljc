(ns brainard.common.navigation.core
  (:require
    #?(:cljs [pushy.core :as pushy])
    [bidi.bidi :as bidi]
    [brainard.common.navigation.routing :as routing]
    [brainard.common.stubs.re-frame :as rf]
    [brainard.common.utils.colls :as colls]
    [brainard.common.utils.keywords :as kw]
    [clojure.string :as string]))

(defn ^:private nav-dispatch [route]
  (rf/dispatch [:routing/navigate route]))

(defn ^:private coerce-params [params coercers]
  (reduce (fn [params [k coercer]]
            (cond-> params
              (contains? params k)
              (update k coercer)))
          params
          coercers))

(defn ->query-string [params]
  (some->> params
           (mapcat (fn [[k v]]
                     (when (some? v)
                       (map (fn [v']
                              (str (name k) "=" (cond-> v' (keyword? v') kw/str)))
                            (cond-> v (not (coll? v)) vector)))))
           seq
           (string/join "&")))

(defn ->query-params [query]
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

(defn match [path]
  (let [[path query-string] (string/split path #"\?")
        route-info (bidi/match-route routing/all path)
        coercers (routing/handler->coercers (:handler route-info))]
    (-> route-info
        (update :route-params coerce-params coercers)
        (assoc :query-params (->query-params query-string)))))

(defonce ^:private link
  #?(:cljs    (doto (pushy/pushy nav-dispatch match)
                pushy/start!)
     :default nil))

(defn with-qp [uri query-params]
  (let [query (->query-string query-params)]
    (cond-> uri
      query (str "?" query))))

(defn path-for
  ([handle]
   (path-for handle nil))
  ([handle params]
   (apply bidi/path-for routing/all handle (flatten (seq params)))))

(defn goto! [uri]
  #?(:cljs
     (pushy/set-token! link uri)))

(defn replace! [uri]
  #?(:cljs
     (pushy/replace-token! link uri)))

(defn navigate!
  ([handle]
   (navigate! handle nil))
  ([handle params]
   (goto! (with-qp (path-for handle params) (:query-params params)))))

(defn update!
  ([handle]
   (update! handle nil))
  ([handle params]
   (replace! (with-qp (path-for handle params) (:query-params params)))))
