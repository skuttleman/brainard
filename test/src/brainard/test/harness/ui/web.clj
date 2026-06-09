(ns brainard.test.harness.ui.web
  (:require
    [slag.utils.edn :as edn]
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

(defn click! [driver q]
  (with-retry 3
    (eta/click driver q))
  (Thread/sleep 5))

(defn wait-optimistic [pred]
  (or (try (eta/wait-predicate #(try (pred)
                                     (catch Throwable _)))
           (catch Throwable _))
      true))

(defn wait-ambiguous []
  (Thread/sleep 100))

(defmulti ^{:arglists '([driver q val])} ^:private fill!
          (fn [driver q _]
            (eta/get-element-tag driver q)))

(defmethod fill! "input"
  [driver q val]
  (eta/clear driver q)
  (eta/fill driver q val))

(defmethod fill! "textarea"
  [driver q val]
  (eta/clear driver q)
  (eta/fill driver q val))

(defmethod fill! "select"
  [driver q val]
  (click! driver q)
  (let [el (eta/query driver q)
        opt (eta/query-from-shadow-root-el driver el {:css (format "option[value='%s']" val)})]
    (eta/click-el driver opt)))

(defn fill-field! [driver label val]
  (let [xpath (format "//*[@id=//label[text()='%s']/@for]" label)]
    (fill! driver {:xpath xpath} val)))

(defn fill-form! [driver form-selector field-vals]
  (eta/wait-visible driver {:css form-selector})
  (doseq [[label value] field-vals]
    (fill-field! driver label value)))

(defn submit-form! [driver form-selector field-vals]
  (fill-form! driver form-selector field-vals)
  (click! driver {:css (str form-selector " button.submit")}))

(defn js-events [driver css-selector events]
  (let [code (apply str
                    (format "var el = document.querySelector('%s');" css-selector)
                    (for [event events]
                      (format "el.dispatchEvent(new MouseEvent('%s', {bubbles: true, cancelable: true}));"
                              (name event))))]
    (eta/js-execute driver code)))

(defmacro with-http-failure [driver msg & body]
  `(try
     (eta/js-execute ~driver (format "window.brainard.test.set_fail_BANG_(%d, '%s')"
                                     (rand-nth [400 401 403 404 500 501 502 503 504])
                                     ~msg))

     ~@body
     (finally
       (eta/js-execute ~driver "window.brainard.test.clear_fail_BANG_()"))))
