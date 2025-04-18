(ns brainard.notes.infra.db
  (:require
    [brainard.api.storage.core :as-alias storage]
    [brainard.api.storage.interfaces :as istorage]
    [brainard.api.utils.fns :as fns]
    [brainard.api.utils.maps :as maps]
    [brainard.infra.db.store :as ds]
    [brainard.notes.api.core :as api.notes]))

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

(defn ^:private prep-attachment-history [_ results]
  (->> results
       (sort-by second)
       (group-by first)
       (mapcat (fn [[k vals]]
                 (reduce-history [:attachments/changes k] vals)))
       (sort-by :notes/history-id)))

(defn ^:private combine-history
  ([note-history attachment-history]
   (combine-history note-history attachment-history []))
  ([[note-hist :as note-history] [att-hist :as attachment-history] combined-history]
   (cond
     (empty? attachment-history)
     (into combined-history note-history)

     (empty? note-history)
     (into combined-history attachment-history)

     (= (:notes/history-id note-hist) (:notes/history-id att-hist))
     (recur (update-in note-history
                       [0 :notes/changes]
                       maps/deep-merge
                       (:notes/changes att-hist))
            (rest attachment-history)
            combined-history)

     (and (> (:notes/history-id note-hist) (:notes/history-id att-hist))
          (-> note-hist :notes/changes :notes/attachments :added))
     (recur (update-in note-history
                       [0 :notes/changes]
                       maps/deep-merge
                       (:notes/changes att-hist))
            (rest attachment-history)
            combined-history)

     (> (:notes/history-id note-hist) (:notes/history-id att-hist))
     (recur note-history
            (rest attachment-history)
            (conj combined-history att-hist))

     :else
     (recur (vec (rest note-history))
            attachment-history
            (conj combined-history note-hist)))))

(defn ^:private prep-history [db results]
  (let [note-history (prep-note-history results)
        attachment-ids (into #{}
                             (keep (fn [[_ _ _ attr val]]
                                     (when (= :notes/attachments attr)
                                       val)))
                             results)
        attachment-history (ds/query db
                                     {:query    '[:find ?e ?tx ?op ?attr ?val ?at ?card
                                                  :in $ [?e ...]
                                                  :where
                                                  [?e ?a ?val ?tx ?op]
                                                  [?tx :db/txInstant ?at]
                                                  [?a :db/ident ?attr]
                                                  [?a :db/cardinality ?c]
                                                  [?c :db/ident ?card]]
                                      :args     [attachment-ids]
                                      :history? true
                                      :post     prep-attachment-history})]
    (combine-history note-history attachment-history)))

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
