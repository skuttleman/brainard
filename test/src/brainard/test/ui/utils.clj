(ns brainard.test.ui.utils
  (:require
    [brainard.infra.utils.edn :as edn]
    [clojure.java.io :as io]
    [etaoin.api :as eta]))

(defn edn-fixture [fixture]
  (->> fixture
       (str "fixtures/")
       io/resource
       edn/read))

(defmacro with-retry [n & body]
  `(loop [tries# ~n]
     (let [[result# ex#] (try
                           [(do ~@body)]
                           (catch Throwable e#
                             [nil e#]))]
       (cond
         (and ex# (pos? tries#)) (recur (dec tries#))
         ex# (throw ex#)
         :else result#))))

(defn click [driver q]
  (with-retry 3
    (eta/click driver q))
  (Thread/sleep 5))

(defmulti ^{:arglists '([driver q val])} fill-field!
          (fn [driver q _]
            (eta/get-element-tag driver q)))

(defmethod fill-field! "input"
  [driver q val]
  (eta/clear driver q)
  (eta/fill driver q val))

(defmethod fill-field! "textarea"
  [driver q val]
  (eta/clear driver q)
  (eta/fill driver q val))

(defmethod fill-field! "select"
  [driver q val]
  (click driver q)
  (let [el (eta/query driver q)
        opt (eta/query-from-shadow-root-el driver el {:css (format "option[value='%s']" val)})]
    (eta/click-el driver opt)))

(defn fill-form! [driver form-selector field-vals]
  (eta/wait-visible driver {:css form-selector})
  (doseq [[label value] field-vals
          :let [xpath (format "//*[@id=//label[text()='%s']/@for]" label)]]
    (fill-field! driver {:xpath xpath} value)))

(defn submit-form! [driver form-selector field-vals]
  (fill-form! driver form-selector field-vals)
  (eta/click driver {:css (str form-selector " button.submit")}))
