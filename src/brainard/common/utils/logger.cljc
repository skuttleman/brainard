(ns brainard.common.utils.logger
  #?(:cljs
     (:require-macros
       brainard.common.utils.logger))
  (:require
    [clojure.string :as string]
    [taoensso.timbre :as log*]))

(defn ^:private log* [form level args]
  `(log*/log! ~level :p ~args {:?line ~(:line (meta form))}))

(defmacro log [level & args]
  (log* &form level args))

(defmacro trace [& args]
  (log* &form :trace args))

(defmacro debug [& args]
  (log* &form (if (:ns &env) :info :debug) args))

(defmacro info [& args]
  (log* &form :info args))

(defmacro warn [& args]
  (log* &form :warn args))

(defmacro error [& args]
  (log* &form :error args))

(defmacro fatal [& args]
  (log* &form :fatal args))

(defmacro report [& args]
  (log* &form :report args))

(defn ^:private filter-external-packages [{:keys [level ?ns-str] :as data}]
  (when (or (#{:warn :error :fatal :report} level)
            (some->> ?ns-str (re-matches #"^brainard.*")))
    data))

(defn ^:private output-fn [{:keys [level msg_ ?ns-str ?file timestamp_ ?line]}]
  (let [loc (str (or ?ns-str ?file "?") ":" (or ?line "?"))]
    (str (when-let [ts (some-> timestamp_ deref)]
           (str ts " "))
         (string/upper-case (name level))
         " [" loc "]: "
         @msg_)))

(log*/merge-config! {:level      (keyword (or #?(:clj (System/getenv "LOG_LEVEL"))
                                              :info))
                     :middleware [filter-external-packages]
                     :output-fn  output-fn})
