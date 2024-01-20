(ns brainard.workspace.api.specs)

(def create
  [:map
   [:workspace-nodes/parent-id {:optional true} uuid?]
   [:workspace-nodes/data string?]])

(def full
  [:schema {:registry {::full (conj create
                                    [:workspace-nodes/id uuid?]
                                    [:workspace-nodes/nodes {:optional true}
                                     [:sequential [:ref ::full]]])}}
   [:ref ::full]])

(def move
  [:map
   [:workspace-nodes/old-parent-id {:optional true} uuid?]
   [:workspace-nodes/new-parent-id uuid?]])
