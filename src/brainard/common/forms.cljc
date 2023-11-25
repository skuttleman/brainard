(ns brainard.common.forms
  (:require
    [brainard.common.utils.maps :as maps]))

(defn current [form]
  (when form
    (maps/nest (:current form))))

(defn errors [{:form/keys [validator] :as form}]
  (when form
    (validator (current form))))

(defn change [form path value]
  (when form
    (assoc-in form [:current path] value)))

(defn changed?
  ([{:keys [current init] :as form}]
   (not= current init))
  ([{:keys [current init] :as form} path]
   (not= (get current path) (get init path))))

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

(defn create [id data validator]
  (let [current (maps/flatten data)]
    {:form/id        id
     :form/validator validator
     :init           current
     :current        current
     :form/attempted false
     :touched        #{}
     :form/touched   false}))
