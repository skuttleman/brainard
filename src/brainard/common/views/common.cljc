(ns brainard.common.views.common
  (:require
    [brainard.common.utils.maps :as maps]))

(defn spinner
  ([]
   (spinner nil))
  ([{:keys [size]}]
   [(keyword (str "div.loader." (name (or size :small))))]))

(defn plain-button [{:keys [disabled] :as attrs} & content]
  (-> attrs
      (maps/assoc-defaults :type :button)
      (assoc :disabled disabled)
      (cond-> disabled (update :class (fnil conj []) "is-disabled"))
      (->> (conj [:button.button]))
      (into content)))

#_(defn with-resource [*resource comp]
  (let [[*resource opts] (colls/force-sequential *resource)
        comp (colls/force-sequential comp)]
    (when-not (res/requested? *resource)
      (res/request! *resource opts))
    (fn [_ _]
      (let [status (res/status *resource)]
        (case status
          :success (conj comp @*resource)
          :error [:div.error [alert :error "An error occurred."]]
          [spinner {:size (:spinner/size opts)}])))))
