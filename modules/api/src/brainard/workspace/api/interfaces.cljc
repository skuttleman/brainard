(ns brainard.workspace.api.interfaces)

(defprotocol IWriteWorkspaceNodes
  "Saves workspace nodes to a store."
  :extend-via-metadata true
  (save! [this node]
    "Saves a workspace node to the store.")
  (delete! [this node-id]
    "Deletes a workspace node and its entire subtree.")
  (detach-node! [this node-id]
    "Moves a node from its parent to the root of the tree")
  (move-node! [this old-parent-id new-parent-id node-id]
    "Moves a node from one parent to another"))

(defprotocol IReadWorkspaceNodes
  "Retrieves workspace nodes from a store."
  :extend-via-metadata true
  (get-all [this]
    "Retrieves the top level workspace nodes and their subtrees")
  (get-by-id [this node-id]
    "Retrieves one node by id and its subtrees"))
