(ns org.broadinstitute.firecloud-ui.paths)

(defn- ws-path [workspace-id]
  (str (:namespace workspace-id) "/" (:name workspace-id)))

(defn list-workspaces-path [] "/workspaces")

(defn workspace-details-path [workspace-id]
  (str "/workspaces/" (:namespace workspace-id) "/" (:name workspace-id)))

(defn create-workspace-path [] "/workspaces")

(defn list-method-configs-path [workspace-id]
  (str "/workspaces/" (ws-path workspace-id) "/methodconfigs"))

(defn get-method-config-path [workspace-id config]
  (str "/workspaces/" (ws-path workspace-id)
    "/method_configs/" (config "namespace") "/" (config "name")))

(defn list-all-entities-path [workspace-id entity-type]
  (str "/workspaces/" (ws-path workspace-id) "/entities/" entity-type))

(defn get-entities-by-type-path [workspace-id]
  (str "/workspaces/" (ws-path workspace-id) "/entities_with_type"))

(defn update-method-config-path [workspace-id config]
  (str "/workspaces/" (ws-path workspace-id)
    "/method_configs/" (config "namespace") "/" (config "name")))

(defn rename-method-config-path [workspace-id config]
  (str "/workspaces/" (ws-path workspace-id)
    "/method_configs/" (config "namespace") "/" (config "name") "/rename"))

(defn copy-method-config-to-workspace-path [workspace-id]
  (str "/workspaces/" (ws-path workspace-id) "/method_configs/copyFromMethodRepo"))

(defn get-methods-path [] "/methods")

(defn submit-method-path [workspace-id]
      (str "/workspaces/" (ws-path workspace-id) "/submissions"))
