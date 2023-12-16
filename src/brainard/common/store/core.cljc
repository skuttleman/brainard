(ns brainard.common.store.core
  #?(:cljs (:require-macros brainard.common.store.core))
  (:require
    [defacto.forms.core :as forms]
    [brainard.common.stubs.reagent :as r]
    [clojure.pprint :as pp]
    [defacto.core :as defacto]
    [defacto.forms.plus :as forms+]
    [defacto.resources.core :as res]))

(defmethod defacto/query-responder ::all
  [db _]
  (select-keys db #{}))

(defn ^:private add-dev-logger! [store]
  (add-watch (defacto/subscribe store [::all])
             (gensym)
             (fn [_ _ _ db]
               (when (seq db)
                 (print "NEW DB ")
                 (pp/pprint db)))))

(defn create
  ([]
   (create nil))
  ([ctx]
   (create ctx nil))
  ([ctx db-value]
   (doto (defacto/create ctx db-value {:->sub r/atom})
     add-dev-logger!)))

(defn dispatch! [store command]
  (defacto/dispatch! store command))

(defn emit! [store event]
  (defacto/emit! store event))

(defn subscribe [store query]
  (defacto/subscribe store query))

(defn query [store query]
  (defacto/query-responder @store query))

(defn ^:private sync* [store resource-key {:keys [data] :as params}]
  (emit! store [::forms/created resource-key data {:remove-nil? true}])
  (dispatch! store [::forms+/submit! resource-key params])
  (when (res/error? (query store [::res/?:resource resource-key]))
    (emit! store [::res/destroyed resource-key])))

(defn init-form! [{:keys [->params init resource-key store]}]
  (sync* store resource-key (->params init))
  (subscribe store [::forms+/?:form+ resource-key]))

(defn qp-syncer [{:keys [->params resource-key store]}]
  (fn [_ _ _ {:keys [query-params]}]
    (let [{:keys [data] :as params} (->params query-params)]
      (when-not (= data (forms/data (query store [::forms+/?:form+ resource-key])))
        (sync* store resource-key params)))))

(defmacro with-qp-sync-form [[form-sym opts & bindings] & body]
  (let [last-line (last body)
        [body fin] (if (and (list? last-line) (= 'finally (first last-line)))
                     [(butlast body) (rest last-line)]
                     [body])
        sub-sym (gensym)
        watch-key (str (gensym))]
    `(r/with-let [{store# :store :as opts#} ~opts
                  ~sub-sym (doto (subscribe store# [:routing/?:route])
                             (add-watch ~watch-key (qp-syncer opts#)))
                  ~form-sym (init-form! opts#)
                  ~@bindings]
       ~@body
       ~(list 'finally
              `(do (remove-watch ~sub-sym ~watch-key)
                   ~@fin)))))
