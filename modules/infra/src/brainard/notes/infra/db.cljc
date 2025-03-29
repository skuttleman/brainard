(ns brainard.notes.infra.db
  (:require
    [brainard.api.storage.core :as-alias storage]
    [brainard.api.storage.interfaces :as istorage]
    [brainard.api.utils.fns :as fns]
    [brainard.infra.db.store :as ds]
    [brainard.notes.api.core :as api.notes]))

(def ^:private select
  '[:find (pull ?e [*]) (min ?at)
    :in $])

(defn ^:private notes-query [{:notes/keys [context ids pinned? tags]}]
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
    (conj ['?e :notes/pinned? true])

    :always
    (conj '[?e _ _ ?tx]
          '[?tx :db/txInstant ?at])))

(defn ^:private prep-history [results]
  (->> results
       (sort-by (juxt first second))
       (reduce (fn [versions [tx op attr val at cardinality]]
                 (let [recent (peek versions)
                       same-tx? (= tx (:notes/history-id recent))
                       version (if same-tx?
                                 recent
                                 {:notes/history-id tx
                                  :notes/saved-at   at
                                  :notes/changes    {}})
                       [k f] (case [cardinality op]
                               [:db.cardinality/one false] [:from (constantly val)]
                               [:db.cardinality/one true] [:to (constantly val)]
                               [:db.cardinality/many false] [:removed (fnil conj #{})]
                               [:db.cardinality/many true] [:added (fnil conj #{})])
                       next-version (update-in version [:notes/changes attr k] f val)]
                   (conj (cond-> versions same-tx? pop) next-version)))
               [])))

(defn ^:private retract-attachments [db {note-id :notes/id :as note}]
  (if-let [attachment-ids (seq (:notes/attachments!remove note))]
    (map (partial conj [:db/retract [:notes/id note-id] :notes/attachments])
         (ds/query db {:query '[:find ?e
                                :in $ [?id ...]
                                :where [?e :attachments/id ?id]]
                       :args  [attachment-ids]
                       :xform (map first)}))
    []))

(defn ^:private clean-note [note]
  (-> note
      (select-keys #{:notes/id
                     :notes/context
                     :notes/body
                     :notes/tags
                     :notes/pinned?
                     :notes/attachments})
      (update :notes/attachments fns/smap select-keys #{:attachments/id :attachments/name})))

(defmethod istorage/->input ::api.notes/create!
  [note]
  [(clean-note note)])

(defmethod istorage/->input ::api.notes/update!
  [{note-id :notes/id retract-tags :notes/tags!remove :as note}]
  (into [(clean-note note)
         #?(:clj  `[retract-attachments ~note]
            :cljs [:db.fn/call retract-attachments note])]
        (map (partial conj [:db/retract [:notes/id note-id] :notes/tags]))
        retract-tags))

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
  {:query    '[:find ?tx ?op ?attr ?val ?at ?card
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
