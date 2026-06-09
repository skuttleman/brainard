(ns brainard.notes.infra.db
  (:require
    [brainard.api.storage.core :as-alias storage]
    [brainard.api.storage.interfaces :as istorage]
    [brainard.infra.db.store :as ds]
    [brainard.notes.api.core :as api.notes]
    [slag.utils.fns :as fns]
    [slag.utils.maps :as maps]))

(def ^:private select
  '[:find (pull ?e [*]) (min ?at)
    :in $])

(defn ^:private notes-query [{:notes/keys [context ids pinned? tags todos]}]
  (cond-> select
    (seq ids)
    (conj '[?id ...])

    :always
    (conj :where)

    (seq ids)
    (conj '[?e :notes/id ?id])

    (some? context)
    (conj ['?e :notes/context context])

    (seq tags)
    (into (map (partial conj '[?e :notes/tags])) tags)

    pinned?
    (conj '[?e :notes/pinned? true])

    (= todos :complete)
    (conj '[?e :notes/todos]
          '(not-join [?e]
                     [?e :notes/todos ?todo]
                     [?todo :todos/completed? false]))

    (= todos :incomplete)
    (conj '[?e :notes/todos ?todo]
          '[?todo :todos/completed? false])

    :always
    (conj '[?e _ _ ?tx]
          '[?tx :db/txInstant ?at])))

(defn ^:private reduce-history [sub-path results]
  (letfn [(one-updater [_ val] val)
          (many-updater [coll val] (conj (or coll #{}) val))]
    (reduce (fn [versions [_ tx op attr val at cardinality]]
              (let [recent (peek versions)
                    same-tx? (= tx (:notes/history-id recent))
                    version (if same-tx?
                              recent
                              {:notes/history-id tx
                               :notes/saved-at   at
                               :notes/changes    {}})
                    [k f] (case [cardinality op]
                            [:db.cardinality/one false] [:from one-updater]
                            [:db.cardinality/one true] [:to one-updater]
                            [:db.cardinality/many false] [:removed many-updater]
                            [:db.cardinality/many true] [:added many-updater])
                    path (into [:notes/changes] (concat sub-path [attr k]))
                    next-version (update-in version path f val)]
                (conj (cond-> versions same-tx? pop) next-version)))
            []
            results)))

(defn ^:private prep-note-history [results]
  (->> results
       (sort-by second)
       (reduce-history [])))

(defn ^:private prep-child-history [changes-k results]
  (->> results
       (sort-by second)
       (group-by first)
       (mapcat (fn [[k vals]]
                 (reduce-history [changes-k k] vals)))
       (sort-by :notes/history-id)))

(defn ^:private combine-history
  ([note-history sub-history sub-k]
   (combine-history note-history sub-history sub-k []))
  ([[note-hist :as note-history] [sub-hist :as sub-history] sub-k combined-history]
   (cond
     (empty? sub-history)
     (into combined-history note-history)

     (empty? note-history)
     (into combined-history sub-history)

     (= (:notes/history-id note-hist) (:notes/history-id sub-hist))
     (recur (update-in note-history
                       [0 :notes/changes]
                       maps/deep-merge
                       (:notes/changes sub-hist))
            (rest sub-history)
            sub-k
            combined-history)

     (and (> (:notes/history-id note-hist) (:notes/history-id sub-hist))
          (-> note-hist :notes/changes sub-k :added))
     (recur (update-in note-history
                       [0 :notes/changes]
                       maps/deep-merge
                       (:notes/changes sub-hist))
            (rest sub-history)
            sub-k
            combined-history)

     (> (:notes/history-id note-hist) (:notes/history-id sub-hist))
     (recur note-history
            (rest sub-history)
            sub-k
            (conj combined-history sub-hist))

     :else
     (recur (vec (rest note-history))
            sub-history
            sub-k
            (conj combined-history note-hist)))))

(def ^:private child-query
  '[:find ?e ?tx ?op ?attr ?val ?at ?card
    :in $ [?e ...]
    :where
    [?e ?a ?val ?tx ?op]
    [?tx :db/txInstant ?at]
    [?a :db/ident ?attr]
    [?a :db/cardinality ?c]
    [?c :db/ident ?card]])

(defn ^:private prep-history [db results]
  (when (seq results)
    (let [note-history (prep-note-history results)
          ids (reduce (fn [ids [_ _ _ attr val]]
                        (case attr
                          :notes/attachments (update ids :attachment-ids conj val)
                          :notes/todos (update ids :todo-ids conj val)
                          ids))
                      {:attachment-ids #{} :todo-ids #{}}
                      results)
          attachment-history (ds/query db
                                       {:query    child-query
                                        :args     [(:attachment-ids ids)]
                                        :history? true
                                        :post     #(prep-child-history :attachments/changes %2)})
          todo-history (ds/query db
                                 {:query    child-query
                                  :args     [(:todo-ids ids)]
                                  :history? true
                                  :post     #(prep-child-history :todos/changes %2)})]
      (-> note-history
          (combine-history attachment-history :notes/attachments)
          (combine-history todo-history :notes/todos)))))

(defn ^:private clean-note [note]
  (-> note
      (select-keys #{:notes/id
                     :notes/context
                     :notes/body
                     :notes/tags
                     :notes/pinned?
                     :notes/attachments
                     :notes/todos})
      (update :notes/attachments fns/smap select-keys #{:attachments/id
                                                        :attachments/content-type
                                                        :attachments/filename
                                                        :attachments/name})
      (update :notes/todos fns/smap select-keys #{:todos/id
                                                  :todos/text
                                                  :todos/completed?})))

(defmethod istorage/->input ::api.notes/create!
  [note]
  [(clean-note note)])

(defmethod istorage/->input ::api.notes/update!
  [{note-id :notes/id :notes/keys [attachments!remove tags!remove todos!remove] :as note}]
  (concat [(clean-note note)]
          (map #(vector :db/retractEntity [:attachments/id %])
               attachments!remove)
          (map #(vector :db/retract [:notes/id note-id] :notes/tags %)
               tags!remove)
          (map #(vector :db/retractEntity [:todos/id %])
               todos!remove)))

(defmethod istorage/->input ::api.notes/delete!
  [{note-id :notes/id}]
  [[:db/retractEntity [:notes/id note-id]]])

(defmethod istorage/->input ::api.notes/get-contexts
  [_]
  {:query '[:find ?context
            :where [_ :notes/context ?context]]
   :xform (map first)})

(defmethod istorage/->input ::api.notes/get-tags
  [_]
  {:query '[:find ?tag
            :where [_ :notes/tags ?tag]]
   :xform (map first)})

(defmethod istorage/->input ::api.notes/get-notes
  [params]
  {:query (notes-query params)
   :args  (some-> (:notes/ids params) vector)
   :xform (map (fn [[note timestamp]]
                 (assoc note :notes/timestamp timestamp)))})

(defmethod istorage/->input ::api.notes/get-note
  [{:notes/keys [id]}]
  {:query (into select '[?note-id
                         :where
                         [?e :notes/id ?note-id]
                         [?e _ _ ?tx]
                         [?tx :db/txInstant ?at]])
   :args  [id]
   :only? true
   :xform (map (fn [[note timestamp]]
                 (assoc note :notes/timestamp timestamp)))})

(defmethod istorage/->input ::api.notes/get-note-history
  [{:notes/keys [id]}]
  {:query    '[:find ?e ?tx ?op ?attr ?val ?at ?card
               :in $ ?id
               :where
               [?e :notes/id ?id]
               [?e ?a ?val ?tx ?op]
               [?tx :db/txInstant ?at]
               [?a :db/ident ?attr]
               [?a :db/cardinality ?c]
               [?c :db/ident ?card]]
   :args     [id]
   :history? true
   :post     prep-history})
