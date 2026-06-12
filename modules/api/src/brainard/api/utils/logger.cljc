(ns brainard.api.utils.logger
  "Logger wrapper."
  #?(:cljs (:require-macros brainard.api.utils.logger))
  (:require
   [taoensso.timbre :as log*]))

(defn ^:private log* [form level args]
  `(log*/log! ~level :p ~args {:?line ~(:line (meta form))}))

(defn ^:private logf* [form level args]
  `(log*/log! ~level :f ~args {:?line ~(:line (meta form))}))

(defmacro log [level & args]
  (log* &form level args))

(defmacro logf [level fmt & args]
  (logf* &form level (cons fmt args)))

(defmacro trace [& args]
  (log* &form :trace args))

(defmacro tracef [& args]
  (logf* &form :trace args))

(defmacro debug [& args]
  (log* &form :debug args))

(defmacro debugf [& args]
  (logf* &form :debug args))

(defmacro info [& args]
  (log* &form :info args))

(defmacro infof [& args]
  (logf* &form :info args))

(defmacro warn [& args]
  (log* &form :warn args))

(defmacro warnf [& args]
  (logf* &form :warn args))

(defmacro error [& args]
  (log* &form :error args))

(defmacro errorf [& args]
  (logf* &form :error args))

(defmacro fatal [& args]
  (log* &form :fatal args))

(defmacro fatalf [& args]
  (logf* &form :fatal args))

(defmacro report [& args]
  (log* &form :report args))

(defmacro reportf [& args]
  (logf* &form :report args))

(defmacro spy [form]
  `(let [[ok?# result#] (try
                          [true ~form]
                          (catch ~(if (:ns &env) :default `Throwable) ex#
                            [false ex#]))]
     (println "*** SPY ***" '~form "=>" ok?#)
     (println result#)
     (when-not ok?# (throw result#))
     result#))

(defn ^:private filter-external-packages [{:keys [level ?ns-str] :as data}]
  (when (or (#{:warn :error :fatal :report} level)
            (some->> ?ns-str (re-matches #"^brainard.*")))
    data))

(defn ^:private colorize [s color]
  (str color s "\u001b[0m"))

(defn red [s]
  (colorize s "\u001b[31m"))

(defn green [s]
  (colorize s "\u001b[32m"))

(defn yellow [s]
  (colorize s "\u001b[33m"))

(defn blue [s]
  (colorize s "\u001b[36m"))

(def ^:private ->level
  {:trace  (blue "TRACE")
   :debug  (blue "DEBUG")
   :info   (green " INFO")
   :warn   (yellow " WARN")
   :error  (red "ERROR")
   :fatal  (red "FATAL")
   :report (green "REPRT")})

(defn ^:private output-fn [{:keys [level msg_ timestamp_ ?err ?file ?line ?ns-str]}]
  (let [loc (str (or ?ns-str ?file "?") ":" (or ?line "?"))]
    (str (when-let [ts (some-> timestamp_ deref)]
           (str ts " "))
         (->level level)
         " [" loc "]: "
         (when ?err (str (pr-str ?err) "\n"))
         @msg_)))

(log*/merge-config! {:level      (keyword (or #?(:clj (System/getenv "LOG_LEVEL"))
                                              :info))
                     :middleware [filter-external-packages]
                     :output-fn  output-fn})
