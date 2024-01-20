(ns brainard.resources.system
  (:require
    [integrant.core :as ig]))

(def config
  {[::const :brainard/apis]
   {:notes     (ig/ref :brainard/notes-api)
    :schedules (ig/ref :brainard/schedules-api)}

   [::const :brainard/notes-api]
   {:store (ig/ref :brainard.stores/notes)}

   [::const :brainard/schedules-api]
   {:store (ig/ref :brainard.stores/schedules)}

   [::const :brainard/workspace-api]
   {:store (ig/ref :brainard.stores/workspace)}

   ;; stores
   [:brainard.stores/notes :brainard/notes-store]
   {:ds-client (ig/ref :brainard.ds/client)}

   [:brainard.stores/schedules :brainard/schedules-store]
   {:ds-client (ig/ref :brainard.ds/client)}

   [:brainard.stores/workspace :brainard/workspace-store]
   {:ds-client (ig/ref :brainard.ds/client)}

   :brainard.ds/client
   {:logger (ig/ref :brainard.ds/storage-logger)}

   :brainard.ds/storage-logger
   {:db-name "brainard"}})