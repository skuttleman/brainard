(ns brainard.infra.store.core
  #?(:cljs (:require-macros brainard.infra.store.core))
  (:require
   [defacto.core :as defacto]
   [defacto.forms.core :as-alias forms]
   [defacto.forms.plus :as-alias forms+]
   [defacto.resources.core :as res]
   [whet.utils.reagent :as r]))

(defn dispatch!
  "Dispatch a command to the defacto store."
  [store command]
  (defacto/dispatch! store command))

(defn emit!
  "Emit an event into the defacto store."
  [store event]
  (defacto/emit! store event))

(defn subscribe
  "Subscribe to a defacto query. Returns a reactive atom that updates when the
   query results update."
  [store query]
  (defacto/subscribe store query))

(defn query
  "Query the store's responder for the given query."
  [store query]
  (defacto/query-responder @store query))

(defn res-sub
  "Ensure the resource identified by spec-key is loaded and return a resource subscription."
  ([store spec-key]
   (res-sub store spec-key nil))
  ([store spec-key opts]
   (-> store
       (dispatch! [::res/ensure! spec-key opts])
       (subscribe [::res/?:resource spec-key]))))

(defn res-init-sub
  "Initializes a resource with a value"
  ([store spec-key init]
   (when (res/init? (query store [::res/?:resource spec-key]))
     (-> store
         (emit! [::submitted spec-key])
         (emit! [::succeeded spec-key init])))
   (subscribe store [::res/?:resource spec-key])))

(defn form-sub
  "Ensure a form resource is initialized and return its subscription."
  ([store form-id init]
   (form-sub store form-id init nil))
  ([store form-id init opts]
   (-> store
       (dispatch! [::forms/ensure! form-id init opts])
       (subscribe [::forms/?:form form-id]))))

(defn form+-sub
  "Ensure a forms+ resource is initialized and return its subscription."
  ([store spec-key init]
   (form+-sub store spec-key init nil))
  ([store spec-key init opts]
   (-> store
       (dispatch! [::forms/ensure! spec-key init opts])
       (subscribe [::forms+/?:form+ spec-key]))))

(defmacro with-let [bindings & body]
  (letfn [(this-ns? [sym]
           #?(:clj
               (let [ns-prefix (namespace sym)
                     prefix-sym (some-> ns-prefix symbol)
                     target-ns 'brainard.infra.store.core]
                 (if (nil? ns-prefix)
                   (when-let [referred-var (get (ns-refers *ns*) sym)]
                     (= (ns-name (:ns (meta referred-var))) target-ns))
                   (or (when-let [aliased-ns (get (ns-aliases *ns*) prefix-sym)]
                         (= (ns-name aliased-ns) target-ns))
                       (when-let [aliased-ns (get-in &env [:ns :requires prefix-sym] prefix-sym)]
                         (= aliased-ns target-ns))
                       (= ns-prefix (str target-ns)))))))]
    (let [cleanup (for [[_ form] (reverse (partition 2 bindings))
                        :let [cleanup (when (and (list? form) (this-ns? (first form)))
                                         (let [[f store k] form]
                                           (when-not (:static (meta k))
                                             (case (name f)
                                               ("res-sub" "res-init-sub") `(emit! ~store [::res/destroyed ~k])
                                               "form-sub" `(emit! ~store [::forms/destroyed ~k])
                                               "form+-sub" `(emit! ~store [::forms+/destroyed ~k])
                                               nil))))]
                        :when cleanup]
                    cleanup)
          [body fin] (let [final (last body)]
                       (if (and (list? final) (= 'finally (first final)))
                         [(butlast body) (rest final)]
                         [body nil]))]
      `(r/with-let ~bindings
         ~@body
         ~(list 'finally `(do ~@(concat fin cleanup)))))))
