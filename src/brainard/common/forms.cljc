(ns brainard.common.forms
  (:require
    [brainard.common.utils.maps :as maps]))

(defn model [form]
  (when form
    (maps/nest (:form/current form))))

(defn change [{:form/keys [validator] :as form} path value]
  (when form
    (cond-> (assoc-in form [:form/current path] value)
      validator (as-> $form (assoc $form :form/errors (validator (model $form)))))))

(defn changed?
  ([{:form/keys [current init] :as form}]
   (not= current init))
  ([{:form/keys [current init] :as form} path]
   (not= (get current path) (get init path))))

(defn touch
  ([form]
   (assoc form :form/touched? true))
  ([form path]
   (update form :form/touched-paths conj path)))

(defn touched?
  ([form]
   (or (:form/touched? form)
       (boolean (seq (:form/touched-paths form)))))
  ([form path]
   (contains? (:form/touched-paths form) path)))

(defn status [form]
  (cond
    (:form/errors form) :error
    (not (changed? form)) :init
    (:form/warnings form) :warning
    (:form/attempting? form) :waiting
    :else :modified))

(defn errors [form]
  (:form/errors form))

(defn warnings [form]
  (:form/warnings form))

(defn attempt [form validator]
  (assoc form
         :form/attempted? true
         :form/attempting? true
         :form/validator validator))

(defn fail-remote [form errors]
  (-> form
      (assoc :form/attempting? false :form/warnings errors)
      (assoc :form/init (:form/current form))))

(defn local-fail [form validator errors]
  (assoc form
         :form/attempted? true
         :form/validator validator
         :form/errors errors))

(defn create [id data]
  (let [current (maps/flatten data)]
    {:form/id            id
     :form/init          current
     :form/current       current
     :form/attempted?    false
     :form/touched-paths #{}
     :form/touched?      false}))
