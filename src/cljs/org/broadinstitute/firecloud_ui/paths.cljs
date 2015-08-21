(ns org.broadinstitute.firecloud-ui.paths)

(defn list-workspaces-path [] "/workspaces")

(defn workspace-details-path [workspace-id]
  (str "/workspaces/" (:namespace workspace-id) "/" (:name workspace-id)))

(defn create-workspace-path [] "/workspaces")

(defn list-method-configs-path [workspace]
  (str "/workspaces/" (workspace "namespace") "/" (workspace "name") "/methodconfigs"))

(defn get-method-config-path [workspace config]
  (str "/workspaces/" (workspace "namespace") "/" (workspace "name")
    "/method_configs/" (config "namespace") "/" (config "name")))

(defn list-all-entity-types-path [workspace]
  (str "/workspaces/" (workspace "namespace") "/" (workspace "name") "/entities"))

(defn list-all-entities-path [workspace entity-type]
  (str "/workspaces/" (workspace "namespace") "/" (workspace "name") "/entities/" entity-type))

(defn get-entities-by-type-path [workspace]
  (str "/workspaces/" (workspace "namespace") "/" (workspace "name") "/entities_with_type"))

(defn update-method-config-path [workspace config]
  (str "/workspaces/" (workspace "namespace") "/" (workspace "name")
    "/method_configs/" (config "namespace") "/" (config "name")))

(defn rename-method-config-path [workspace config]
  (str "/workspaces/" (workspace "namespace") "/" (workspace "name")
    "/method_configs/" (config "namespace") "/" (config "name") "/rename"))

(defn copy-method-config-to-workspace-path [workspace]
  (str "/workspaces/" (workspace "namespace") "/" (workspace "name") "/method_configs/copyFromMethodRepo"))

(defn get-methods-path [] "/methods")

(defn submit-method-path [workspace]
      (str "/workspaces/" (workspace "namespace") "/" (workspace "name") "/submissions"))
