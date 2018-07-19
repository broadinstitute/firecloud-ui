(ns broadfcui.endpoints
  (:require
   [broadfcui.config :as config]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   ))


(defn ajax-payload [endpoint arg-map]
  (dissoc
   (assoc arg-map
     :method (:method endpoint)
     :data (if-let [raw-data (:raw-data arg-map)]
             raw-data
             (if-let [payload (:payload arg-map)]
               (utils/->json-string payload))))
   :endpoint :raw-data :payload))

(defn call-ajax-orch [{:keys [endpoint] :as arg-map}]
  (ajax/call-orch
   (:path endpoint)
   (ajax-payload endpoint arg-map)))

(defn call-ajax-leo [{:keys [endpoint] :as arg-map}]
  (ajax/call-leo
   (:path endpoint)
   (ajax-payload endpoint arg-map)))

(defn call-ajax-sam [{:keys [endpoint] :as arg-map}]
  (ajax/call-sam
   (:path endpoint)
   (ajax-payload endpoint arg-map)))

(defn- id-path [id]
  (str (:namespace id) "/" (:name id)))

(def list-workspaces
  {:path "/workspaces"
   :method :get})

(def create-workspace
  {:path "/workspaces"
   :method :post})

(defn get-workspace [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id))
   :method :get})

(defn delete-workspace [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id))
   :method :delete})

(defn get-workspace-acl [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/acl")
   :method :get})

(defn check-bucket-read-access [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/checkBucketReadAccess")
   :method :get})

(defn update-workspace-acl [workspace-id invite-new?]
  {:path (str "/workspaces/" (id-path workspace-id) "/acl?inviteUsersNotFound=" (true? invite-new?))
   :method :patch})

(defn clone-workspace [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/clone")
   :method :post})

(defn lock-or-unlock-workspace [workspace-id locked-now?]
  {:path (str "/workspaces/" (id-path workspace-id) (if locked-now? "/unlock" "/lock"))
   :method :put})


(defn list-workspace-method-configs [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/methodconfigs?allRepos=true")
   :method :get})

(defn post-workspace-method-config [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/methodconfigs")
   :method :post})

(defn get-permission-report [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/permissionReport")
   :method :post})

(defn get-workspace-method-config [workspace-id config-id]
  {:path (str "/workspaces/" (id-path workspace-id)
              "/method_configs/" (id-path config-id))
   :method :get})

(defn update-workspace-method-config [workspace-id config-id]
  {:path (str "/workspaces/" (id-path workspace-id)
              "/method_configs/" (id-path config-id))
   :method :post})

(defn delete-workspace-method-config [workspace-id config-id]
  {:path (str "/workspaces/" (id-path workspace-id)
              "/method_configs/" (id-path config-id))
   :method :delete})

(defn get-validated-workspace-method-config [workspace-id config-id]
  {:path (str "/workspaces/" (id-path workspace-id)
              "/method_configs/" (id-path config-id) "/validate")
   :method :get})

(defn update-workspace-attrs [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/updateAttributes")
   :method :patch})

(defn set-workspace-attributes [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/setAttributes")
   :method :patch})

(defn get-workspace-genomic-operations [workspace-id job-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/genomics/operations/" job-id)
   :method :get})

(defn import-entities [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/importEntities")
   :method :post})

(defn import-attributes [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/importAttributesTSV")
   :method :post})

(defn get-entity-types [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/entities")
   :method :get})

(defn get-entities-of-type [workspace-id type]
  {:path (str "/workspaces/" (id-path workspace-id) "/entities/" type)
   :method :get})

(defn get-entity [workspace-id type entity-name]
  {:path (str "/workspaces/" (id-path workspace-id) "/entities/" type "/" entity-name)
   :method :get})

(defn get-entities-paginated [workspace-id type query-parameters]
  (assert (= #{"page" "pageSize" "sortField" "sortDirection" "filterTerms"}
             (set (keys query-parameters)))
          (str "Malformed query parameters: " query-parameters))
  {:path (str "/workspaces/" (id-path workspace-id) "/entityQuery/" type "?"
              (clojure.string/join "&" (keep (fn [[k v]]
                                               (some->> v str not-empty (str k "=")))
                                             query-parameters)))
   :method :get})

(defn get-entities-by-type [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/entities_with_type")
   :method :get})

(defn delete-entities [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/entities/delete")
   :method :post})


(defn list-submissions [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/submissions")
   :method :get})

(defn count-submissions [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/submissionsCount")
   :method :get})


(defn create-cluster [google-project cluster-name]
  {:path (str "/cluster/" google-project "/" cluster-name)
   :method :put})

(defn delete-cluster [google-project cluster-name]
  {:path (str "/cluster/" google-project "/" cluster-name)
   :method :delete})

(def get-clusters-list
  {:path (str "/clusters")
   :method :get})

(defn stop-cluster [google-project cluster-name]
  {:path (str "/cluster/" google-project "/" cluster-name "/stop")
   :method :post})

(defn start-cluster [google-project cluster-name]
  {:path (str "/cluster/" google-project "/" cluster-name "/start")
   :method :post})

(defn get-cluster-details [google-project cluster-name]
  {:path (str "/cluster/" google-project "/" cluster-name)
   :method :get})

(defn create-submission [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/submissions")
   :method :post})

(defn get-submission [workspace-id submission-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/submissions/" submission-id)
   :method :get})

(defn get-workflow-details [workspace-id submission-id workflow-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/submissions/" submission-id
              "/workflows/" workflow-id)
   :method :get})

(defn get-workflow-cost [workspace-id submission-id workflow-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/submissions/" submission-id
              "/workflows/" workflow-id "/cost/")
   :method :get})

(defn abort-submission [workspace-id submission-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/submissions/" submission-id)
   :method :delete})

(defn storage-cost-estimate [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id) "/storageCostEstimate")
   :method :get})

(defn proxy-group [email]
  {:path (str "/proxyGroup/" email)
   :method :get})

(defn pet-token [project]
  {:path (str "/google/v1/user/petServiceAccount/" project "/token")
   :method :post})

(defn list-method-snapshots [namespace name]
  {:path (str "/methods?namespace=" namespace "&name=" name)
   :method :get})

(def list-methods
  {:path "/methods"
   :method :get})

(def list-method-definitions
  {:path "/methods/definitions"
   :method :get})

(def post-method
  {:path "/methods"
   :method :post})

;; I would have called it "get-method" but that's already defined in core
(defn get-agora-method [namespace name snapshot-id]
  {:path (str "/methods/" namespace "/" name "/" snapshot-id)
   :method :get})

(defn get-agora-method-configs [{:keys [namespace name]}]
  {:path (str "/methods/" namespace "/" name "/configurations")
   :method :get})

(defn get-agora-compatible-configs [{:keys [namespace name snapshot-id]}]
  {:path (str "/methods/" namespace "/" name "/" snapshot-id "/configurations")
   :method :get})

(defn create-new-method-snapshot [namespace name snapshot-id & [redact?]]
  {:path (str "/methods/" namespace "/" name "/" snapshot-id "?redact=" (boolean redact?))
   :method :post})


(defn get-configuration [namespace name snapshot-id & [payload-as-object?]]
  {:path (str "/configurations/" namespace "/" name "/" snapshot-id
              (when payload-as-object? "?payloadAsObject=true"))
   :method :get})

(defn copy-method-config-to-repo [workspace-id]
  {:path (str "/workspaces/" (id-path workspace-id)
              "/method_configs/copyToMethodRepo")
   :method :post})

(def create-template
  {:path "/template"
   :method :post})

(def get-inputs-outputs
  {:path "/inputsOutputs"
   :method :post})

(defn- get-agora-base [config?]
  (if config? "configurations" "methods"))

(defn get-agora-entity-acl [config? {:keys [name namespace snapshotId]}]
  {:path (str "/" (get-agora-base config?) "/" namespace "/" name "/" snapshotId "/permissions")
   :method :get})

(defn persist-agora-entity-acl [config? {:keys [name namespace snapshotId]}]
  {:path (str "/" (get-agora-base config?) "/" namespace "/" name "/" snapshotId "/permissions")
   :method :post})

(def multi-grant-method-acl
  {:path "/methods/permissions"
   :method :put})


(defn get-agora-namespace-acl [namespace config?]
  {:path (str "/" (get-agora-base config?) "/" namespace "/permissions")
   :method :get})

(defn post-agora-namespace-acl [namespace config?]
  {:path (str "/" (get-agora-base config?) "/" namespace "/permissions")
   :method :post})


(defn delete-agora-entity [config? {:keys [namespace name snapshotId]}]
  {:path (str "/" (get-agora-base config?) "/" namespace "/" name "/" snapshotId)
   :method :delete})


(defn copy-entity-to-workspace [workspace-id re-link-soft-conflicts?]
  {:path (str "/workspaces/" (id-path workspace-id)
              "/entities/copy?linkExistingEntities=" (boolean re-link-soft-conflicts?))
   :method :post})


(defn get-gcs-stats [bucket object]
  {:path (str "/storage/" bucket "/" object)
   :method :get})


(defn profile-get [on-done]
  (ajax/call-orch "/profile" {:on-done on-done} :service-prefix "/register"))


(defn profile-set [payload on-done]
  (ajax/call-orch
   "/profile"
   {:method :post
    :data (utils/->json-string payload)
    :on-done on-done
    :headers ajax/content-type=json}
   :service-prefix "/register"))


(defn profile-get-nih-status [on-done]
  (ajax/call-orch
   "/nih/status"
   {:on-done on-done}))


(defn profile-link-nih-account [token on-done]
  (ajax/call-orch
   "/nih/callback"
   {:method :post
    :data (utils/->json-string {:jwt token})
    :on-done on-done
    :headers ajax/content-type=json}))


(defn get-groups [on-done]
  (call-ajax-orch
   {:endpoint {:path "/groups"
               :method :get}
    :on-done (fn [{:keys [success? status-text get-parsed-response]}]
               (if success?
                 (on-done nil (get-parsed-response))
                 (on-done status-text nil)))}))

(defn get-ws-access-instructions [workspace-id on-done]
  (call-ajax-orch
   {:endpoint {:path (str "/workspaces/" (id-path workspace-id) "/accessInstructions")
               :method :get}
    :on-done (fn [{:keys [success? status-text get-parsed-response]}]
               (if success?
                 (on-done nil (get-parsed-response))
                 (on-done status-text nil)))}))

(defn create-group [group-name]
  {:path (str "/groups/" group-name)
   :method :post})

(defn delete-group [group-name]
  {:path (str "/groups/" group-name)
   :method :delete})

(defn list-group-members [group-name]
  {:path (str "/groups/" group-name)
   :method :get})

(defn add-group-user [{:keys [group-name role email]}]
  {:path (str "/groups/" group-name "/" role "/" email)
   :method :put})

(defn delete-group-user [{:keys [group-name role email]}]
  {:path (str "/groups/" group-name "/" role "/" email)
   :method :delete})

(defn request-group-access [group-name]
  {:path (str "/groups/" group-name "/requestAccess")
   :method :post})


(defn get-billing-projects
  ([on-done] (get-billing-projects false on-done))
  ([include-pending? on-done]
   (call-ajax-orch
    {:endpoint {:path "/profile/billing" :method :get}
     :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                (if success?
                  (let [pred (if include-pending?
                               (constantly true)
                               #(= (:creationStatus %) "Ready"))]
                    (on-done nil (filterv pred (get-parsed-response))))
                  (on-done status-text nil)))})))

(defn get-billing-project-status [project-name on-done]
  (get-billing-projects
   true
   (fn [err-text projects]
     (if err-text
       (on-done nil)
       (let [project (first (filter #(= project-name (:projectName %)) projects))]
         (on-done (:creationStatus project) (:message project)))))))

(defn get-billing-accounts []
  {:path "/profile/billingAccounts"
   :method :get})

(def create-billing-project
  {:path "/billing"
   :method :post})

(defn list-billing-project-members [project-id]
  {:path (str "/billing/" project-id "/members")
   :method :get})

(defn add-billing-project-user [{:keys [project-id role user-email]}]
  {:path (str "/billing/" project-id "/" role "/" user-email)
   :method :put})

(defn delete-billing-project-user [{:keys [project-id role user-email]}]
  {:path (str "/billing/" project-id "/" role "/" user-email)
   :method :delete})

(defn get-library-groups [on-done]
  (ajax/call-orch
   "/library/groups"
   {:method :get
    :on-done on-done}))

(defn get-library-attributes [on-done]
  (ajax/call-orch
   "/library-attributedefinitions-v1"
   {:method :get
    :on-done on-done}
   :service-prefix "/schemas"))

(defn get-consent [orsp-id on-done]
  (ajax/call-orch
   (str "/duos/consent/orsp/" (js/encodeURIComponent orsp-id))
   {:method :get
    :on-done on-done}))

(defn save-library-metadata [workspace-id]
  {:path (str "/library/" (id-path workspace-id) "/metadata")
   :method :put})

(defn save-discoverable-by-groups [workspace-id]
  {:path (str "/library/" (id-path workspace-id) "/discoverableGroups")
   :method :put})

(defn publish-workspace [workspace-id]
  {:path (str "/library/" (id-path workspace-id) "/published")
   :method :post})

(defn unpublish-workspace [workspace-id]
  {:path (str "/library/" (id-path workspace-id) "/published")
   :method :delete})

(def get-library-curator-status
  {:path "/library/user/role/curator"
   :method :get})

(def search-datasets
  {:path "/library/search"
   :method :post})

(defn submissions-queue-status []
  {:path "/submissions/queueStatus"
   :method :get})

(defn get-cromwell-version [on-done]
  (ajax/call-orch
   "/executionEngine"
   {:method :get
    :on-done on-done}
   :service-prefix "/version"))

(defn dockstore-get-wdl [method-path method-version on-done]
  (ajax/call {:url (str (config/dockstore-api-url)
                        "/api/ga4gh/v1/tools/%23workflow%2F"
                        (js/encodeURIComponent method-path)
                        "/versions/"
                        (js/encodeURIComponent method-version)
                        "/WDL/descriptor")
              :on-done on-done}))

(defn dockstore-get-versions [method-path on-done]
  (ajax/call {:url (str (config/dockstore-api-url)
                        "/api/ga4gh/v1/tools/%23workflow%2F"
                        (js/encodeURIComponent method-path)
                        "/versions")
              :on-done on-done}))

(def import-status
  {:path "/profile/importstatus"
   :method :get})
