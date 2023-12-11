(ns brainard.common.forms.core
  (:require
    [brainard.common.utils.maps :as maps]))

(defn id [form]
  (::id form))

(defn data
  "Extract the canonical model of data from the form."
  [form]
  (when form
    (maps/nest (::current form))))

(defn change
  "Changes a value of a form at a path.

  (-> form
      (change [:some :path] 42)
      data)
  ;; => {:some {:path 42}}"
  [form path value]
  (when form
    (if (and (nil? value) (-> form ::opts :remove-nil?))
      (update form ::current dissoc path)
      (assoc-in form [::current path] value))))

(defn changed?
  "Does the current value of the form differ from the initial value?"
  ([{::keys [current init]}]
   (not= current init))
  ([{::keys [current init]} path]
   (not= (get current path) (get init path))))

(defn create
  "Creates a form from a model. Supported opts

  :remove-nil?   - when true, calls to [[change]] will remove the path instead of setting it.
                   defaults to `false`."
  [id data opts]
  {:pre [(or (nil? data) (map? data))]}
  (let [internal-data (maps/flatten data)]
    {::id      id
     ::init    internal-data
     ::current internal-data
     ::opts    opts}))
