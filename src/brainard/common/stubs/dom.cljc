(ns brainard.common.stubs.dom)

(defn prevent-default! [e]
  (some-> e .preventDefault)
  e)

(defn stop-propagation! [e]
  (some-> e .stopPropagation)
  e)

(defn target-value [e]
  (some-> e .-target .-value))

(defn click! [node]
  (some-> node .click)
  node)

(defn blur! [node]
  (some-> node .blur)
  node)

(defn focus! [node]
  (some-> node .focus)
  node)
