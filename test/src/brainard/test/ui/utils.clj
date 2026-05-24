(ns brainard.test.ui.utils
  (:require
    [etaoin.api :as eta]))

(defn fill-form! [driver form-selector field-vals]
  (eta/wait-visible driver {:css form-selector})
  (doseq [[label value] field-vals
          :let [xpath (format "//input[@id=//label[text()='%s']/@for]" label)
                el (eta/query driver {:xpath xpath})]]
    (eta/clear-el driver el)
    (eta/fill-el driver el value)))

(defn submit-form! [driver form-selector field-vals]
  (fill-form! driver form-selector field-vals)
  (eta/click driver {:css (str form-selector " button.submit")}))
