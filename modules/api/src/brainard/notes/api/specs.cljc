(ns brainard.notes.api.specs
  (:require
    [brainard.api.specs :as scommon]
    [brainard.attachments.api.specs :as sattachments]
    [brainard.schedules.api.specs :as ssched]
    [malli.util :as mu]))

(def todo-create
  [:map
   [:todos/text scommon/non-empty-trimmed-string]
   [:todos/completed? boolean?]])

(def ^:private todo-full
  (mu/merge todo-create
            [:map
             [:todos/id uuid?]]))

(def ^:private todo-update
  [:map
   [:todos/id uuid?]
   [:todos/text {:optional true} scommon/non-empty-trimmed-string]
   [:todos/completed? {:optional true} boolean?]])

(def create
  [:map
   [:notes/context scommon/non-empty-trimmed-string]
   [:notes/body scommon/non-empty-string]
   [:notes/pinned? boolean?]
   [:notes/tags {:optional true} [:set keyword?]]
   [:notes/attachments {:optional true} [:seqable sattachments/full]]
   [:notes/todos {:optional true} [:seqable todo-create]]])

(def full
  (mu/merge create
            [:map
             [:notes/id uuid?]
             [:notes/timestamp inst?]
             [:notes/schedules {:optional true} [:sequential ssched/full]]
             [:notes/todos {:optional true} [:seqable todo-full]]]))

(def history
  [:map
   [:notes/history-id int?]
   [:notes/saved-at inst?]
   [:notes/changes
    [:map
     [:notes/id {:optional true} [:map [:to uuid?]]]
     [:notes/context {:optional true} [:map
                                       [:from {:optional true} scommon/non-empty-trimmed-string]
                                       [:to {:optional true} scommon/non-empty-trimmed-string]]]
     [:notes/body {:optional true} [:map
                                    [:from {:optional true} scommon/non-empty-string]
                                    [:to {:optional true} scommon/non-empty-string]]]
     [:notes/pinned? {:optional true} [:map
                                       [:from {:optional true} boolean?]
                                       [:to {:optional true} boolean?]]]
     [:notes/tags {:optional true} [:map
                                    [:added {:optional true} [:set keyword?]]
                                    [:removed {:optional true} [:set keyword?]]]]
     [:notes/attachments {:optional true} [:map
                                           [:added {:optional true} [:set int?]]
                                           [:removed {:optional true} [:set int?]]]]
     [:attachments/changes {:optional true} [:map-of
                                             int?
                                             [:map
                                              [:attachments/id {:optional true}
                                               [:map
                                                [:from {:optional true} uuid?]
                                                [:to {:optional true} uuid?]]]
                                              [:attachments/name {:optional true}
                                               [:map
                                                [:from {:optional true} scommon/non-empty-string]
                                                [:to {:optional true} scommon/non-empty-string]]]
                                              [:attachments/filename {:optional true}
                                               [:map
                                                [:from {:optional true} scommon/non-empty-string]
                                                [:to {:optional true} scommon/non-empty-string]]]
                                              [:attachments/content-type {:optional true}
                                               [:map
                                                [:from {:optional true} scommon/non-empty-string]
                                                [:to {:optional true} scommon/non-empty-string]]]]]]]]])

(def modify
  [:map
   [:notes/context {:optional true} scommon/non-empty-trimmed-string]
   [:notes/body {:optional true} scommon/non-empty-string]
   [:notes/tags {:optional true} [:set keyword?]]
   [:notes/tags!remove {:optional true} [:set keyword?]]
   [:notes/pinned? {:optional true} boolean?]
   [:notes/attachments {:optional true} [:seqable sattachments/modify]]
   [:notes/attachments!remove {:optional true} [:set uuid?]]
   [:notes/todos {:optional true} [:seqable todo-update]]
   [:notes/todos!remove {:optional true} [:set uuid?]]])

(def query
  [:and
   [:map
    [:notes/ids {:optional true} [:set uuid?]]
    [:notes/context {:optional true} scommon/non-empty-trimmed-string]
    [:notes/tags {:optional true} [:set keyword?]]
    [:notes/pinned? {:optional true} true?]
    [:notes/todos {:optional true} [:enum {} nil :complete :incomplete]]]
   [:fn {:error/message "must select at least one: ids, tag, topic, or pinned"}
    (some-fn (comp seq :notes/ids)
             (comp true? :notes/pinned?)
             (comp some? :notes/context)
             (comp seq :notes/tags)
             (comp some? :notes/todos))]])
