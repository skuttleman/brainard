(ns brainard.infra.views.pages.note.history
  (:require
    [brainard.api.utils.dates :as dates]
    [brainard.infra.store.core :as store]
    [brainard.infra.store.specs :as-alias specs]
    [brainard.infra.views.components.core :as comp]
    [brainard.infra.views.components.interfaces :as icomp]
    [defacto.resources.core :as res]
    [whet.utils.reagent :as r]))

(defn ^:private tag-list [note]
  (if-let [tags (not-empty (:notes/tags note))]
    [comp/tag-list {:value tags}]
    [:em "no tags"]))

(defn ^:private history-change [label {:keys [added from removed to]}]
  [:div.layout--row
   [:span.purple label]
   (when (some? added)
     [:<>
      [:em.space--left "added"]
      [:span.space--left.truncate.blue
       (apply str (interpose ", " added))]])
   (when (some? removed)
     [:<>
      [:em.space--left "removed"]
      [:span.space--left.truncate.orange
       (apply str (interpose ", " removed))]])
   (cond
     (and (some? from) (some? to)) [:<>
                                    [:em.space--left "changed"]
                                    [:span.space--left.truncate.orange {:style {:max-width "50%"}}
                                     (str from)]
                                    [:em.space--left "to"]
                                    [:span.space--left.truncate.blue {:style {:max-width "50%"}}
                                     (str to)]]
     (some? from) [:<>
                   [:em.space--left "removed"]
                   [:span.space--left.truncate.orange
                    (str from)]]
     (some? to) [:<>
                 [:em.space--left "added"]
                 [:span.space--left.truncate.blue
                  (str to)]])])

(defmethod icomp/modal-header ::view
  [_ {:notes/keys [saved-at]}]
  (dates/->str saved-at))

(defmethod icomp/modal-body ::view
  [*:store {modal-id :modals/id :keys [last? note] :as params}]
  [:div.layout--stack-between
   [:div.layout--row
    (when (:notes/pinned? note)
      [comp/icon {:class ["layout--space-after"]
                  :style {:align-self :center}} :paperclip])
    [:h1 [:strong (:notes/context note)]]]
   [comp/markdown (:notes/body note)]
   [tag-list note]
   (when-not last?
     [:div
      [comp/plain-button {:*:store  *:store
                          :class    ["is-small" "is-info"]
                          :commands [[:modals/remove! modal-id]
                                     [::res/submit! [::specs/notes#reinstate modal-id] params]]}
       "reinstate"]])])

(defn ^:private note-history [*:store reconstruction entries]
  (let [last-idx (dec (count entries))
        prev-tags (:notes/tags (get reconstruction (:notes/history-id (last entries))))]
    [:ul.note-history
     (for [[idx {:notes/keys [changes history-id saved-at]}] (map-indexed vector entries)
           :let [history-modal [::view {:last?     (= idx last-idx)
                                        :note      (get reconstruction history-id)
                                        :prev-tags prev-tags}]]]
       ^{:key history-id}
       [:li.layout--stack-between
        [:div.layout--row.layout--align-center.layout--space-between
         [:div
          [:span.layout--space-after.green (dates/->str saved-at)]]
         [comp/plain-button {:*:store  *:store
                             :class    ["is-small" "is-info"]
                             :commands [[:modals/create! history-modal]]}
          "show"]]
        (into [:<>]
              (for [[k label] [[:notes/context "Topic"]
                               [:notes/pinned? "Pin"]
                               [:notes/body "Body"]
                               [:notes/tags "Tags"]]
                    :let [change (k changes)]
                    :when change]
                [history-change label change]))])]))

(defmethod icomp/modal-header ::modal
  [_ _]
  "Note's change history")

(defmethod icomp/modal-body ::modal
  [*:store {{note-id :notes/id} :note}]
  (r/with-let [spec-key [::specs/note#history note-id]
               sub:history (store/res-sub *:store spec-key)
               sub:recon (store/subscribe *:store [:notes.history/?:reconstruction spec-key])]
    [comp/with-resource sub:history [note-history *:store @sub:recon]]
    (finally
      (store/emit! *:store [::res/destroyed spec-key]))))
