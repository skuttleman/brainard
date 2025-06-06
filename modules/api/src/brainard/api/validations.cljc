(ns brainard.api.validations
  "Specs for data that flows through the system."
  (:require
    [brainard.notes.api.specs :as snotes]
    [brainard.schedules.api.specs :as ssched]
    [brainard.workspace.api.specs :as sws]
    [malli.core :as m]
    [malli.error :as me]))

(def input-specs
  {:api.notes/select            snotes/query
   :api.notes/create!           snotes/create
   :api.notes/fetch             [:map [:notes/id uuid?]]
   :api.notes/fetch?history     [:map [:notes/id uuid?]]
   :api.notes/delete!           [:map [:notes/id uuid?]]
   :api.notes/update!           snotes/modify

   :api.schedules/create!       ssched/create
   :api.schedules/delete!       [:map [:schedules/id uuid?]]

   :api.workspace-nodes/create! sws/create
   :api.workspace-nodes/delete! [:map [:workspace-nodes/id uuid?]]
   :api.workspace-nodes/update! sws/modify

   :api.attachments/upload!     [:any]})

(def output-specs
  {:api.notes/select                [:sequential snotes/full]
   :api.notes/fetch                 snotes/full
   :api.notes/fetch?history         [:sequential snotes/history]
   :api.notes/create!               snotes/full
   :api.notes/update!               snotes/full
   :api.tags/select                 [:set keyword?]
   :api.contexts/select             [:set string?]

   :api.schedules/create!           ssched/full

   :api.workspace-nodes/select-tree [:sequential sws/full]})

(defn ^:private throw! [type params]
  (throw (ex-info "failed spec validation" (assoc params ::type type))))

(defn ->validator
  "Creates function that validates a value against a spec.

   (def malli-spec
     [:map
      [:first-name string?]
      [:last-name string?]])

   (def validator (->validator malli-spec))

   (validator {:first-name 3})
   ;; => {:first-name [\"must be a string\"] :last-name [\"missing\"]}"
  [spec]
  (fn [data]
    (when-let [errors (m/explain spec data)]
      (me/humanize errors))))

(defn validate!
  "Validates a value against a spec and throws an exception with ::type `type`."
  [spec data type]
  (let [validator (->validator spec)]
    (when-let [details (validator data)]
      (throw! type {:data data :details details}))))

(defn select-spec-keys [m spec]
  (let [spec (m/form spec)]
    (if-not (seqable? spec)
      m
      (let [[kind & more] spec
            spec' (last more)]
        (case kind
          :and (recur m (second spec))
          :map (into {}
                     (keep (fn [[kind & more]]
                             (when-let [[_ v] (find m kind)]
                               (let [spec' (last more)]
                                 [kind (select-spec-keys v spec')]))))
                     more)
          :map-of (into {}
                        (map (fn [[k v]]
                               [k (select-spec-keys v spec')]))
                        m)
          :set (into #{}
                     (map #(select-spec-keys % spec'))
                     m)
          (:seqable :sequential) (into []
                                       (map #(select-spec-keys % spec'))
                                       m)
          m)))))
