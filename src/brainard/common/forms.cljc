(ns brainard.common.forms
  (:require
    [brainard.common.utils.maps :as maps]))

(defn current [form]
  (maps/nest (:current form)))

(defn errors [{:form/keys [validator] :as form}]
  (validator (current form)))

(defn change [form path value]
  (assoc-in form [:current path] value))

(defn changed?
  ([{:keys [current init] :as form}]
   (= current init))
  ([{:keys [current init] :as form} path]
   (= (get current path) (get init path))))

(defn touch
  ([form]
   (assoc form :form/touched true))
  ([form path]
   (update form :touched conj path)))

(defn touched?
  ([form]
   (or (:form/touched form)
       (boolean (seq (:touched form)))))
  ([form path]
   (contains? (:touched form) path)))

(defn attempt [form]
  (assoc form :form/attempted true))

(defn create
  ([id data]
   (create id data (constantly nil)))
  ([id data validator]
   (let [internal (maps/flatten data)]
     {:form/id        id
      :form/validator validator
      :init           internal
      :current        internal
      :form/attempted false
      :touched        #{}
      :form/touched   false})))
