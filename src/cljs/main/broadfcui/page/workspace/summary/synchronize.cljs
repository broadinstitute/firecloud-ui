(ns broadfcui.page.workspace.summary.synchronize
  (:require
   [dmohs.react :as react]
   [broadfcui.utils :as utils]
   ))


(defn handle-sync [parsed-perms-report]
  (let [new-users (map name (keys (:workspaceACL parsed-perms-report)))
        methods (:referencedMethods parsed-perms-report)
        methods-we-dont-own (filter (comp empty? :acls) methods)
        methods-we-own (filter (comp seq :acls) methods)]
    (utils/log methods-we-dont-own)))
