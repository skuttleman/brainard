(ns brainard.notes.infra.export
  (:require
    [clojure.string :as string]
    [hiccup2.core :as hiccup]))

(defn ^:private with-tags [md tags]
  (str md (hiccup/html [:ul {:style {:list-style   :none
                                     :padding-left 0}}
                        (for [tag (sort-by (juxt namespace name) tags)]
                          [:li {:style {:background-color "rgba(50, 50, 50, 100)"
                                        :border-radius    "3px"
                                        :color            :lightblue
                                        :display          :inline-block
                                        :margin-right     "8px"
                                        :padding          "2px 4px"}}
                           (str tag)])])))

(defn ^:private with-todos [md todos]
  (let [todos (for [todo (->> todos
                              (sort-by (juxt (complement :todos/completed?) :todos/text)))]
                (str "- ["
                     (if (:todos/completed? todo) \x \space)
                     "] "
                     (:todos/text todo) \newline))]
    (apply str md "## TODOs" \newline \newline todos)))

(defn ^:private with-attachments [md host attachments]
  (let [attachments (for [att (->> attachments
                                   (sort-by (juxt :attachments/name :attachments/id)))
                          :let [href (str host "/attachments/" (:attachments/id att))]]
                      (str "- "
                           (hiccup/html [:a {:href   href
                                             :target "_blank"}
                                         (:attachments/name att)])
                           \newline))]
    (apply str md "## Attachments" \newline \newline attachments)))

(defn ->markdown [host {:notes/keys [attachments body context pinned? tags todos]}]
  (cond-> (str "# " context)
    pinned? (str " [Pinned]")
    :always (str \newline \newline
                 body \newline \newline)
    (seq tags) (-> (with-tags tags) (str \newline \newline))
    (seq todos) (-> (with-todos todos) (str \newline))
    (seq attachments) (with-attachments host attachments)
    :always string/trim))
