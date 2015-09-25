(ns org.broadinstitute.firecloud-ui.endpoints
  (:require
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn call-ajax-orch [{:keys [endpoint] :as arg-map}]
  (utils/ajax-orch (:path endpoint)
    (dissoc
      (assoc arg-map
        :method (:method endpoint)
        :data (if-let [raw-data (:raw-data arg-map)]
                raw-data
                (if-let [payload (:payload arg-map)]
                  (utils/->json-string payload)))
        :canned-response {:status 200 :delay-ms (rand-int 2000)
                          :responseText (if-let [mock-data (:mock-data endpoint)]
                                          (utils/->json-string mock-data))})
      :endpoint :raw-data :payload)))


(defn- ws-path [workspace-id]
  (str (:namespace workspace-id) "/" (:name workspace-id)))

(def list-workspaces
  {:path "/workspaces"
   :method :get
   :mock-data
   (map
     (fn [i]
       (let [ns (rand-nth ["broad" "public" "nci"])
             status (rand-nth ["Complete" "Running" "Exception"])]
         {:accessLevel (rand-nth ["OWNER" "WRITER" "READER"])
          :workspace {:namespace ns
                      :name (str "Workspace " (inc i))
                      :status status
                      :createdBy ns
                      :createdDate (.toISOString (js/Date.))}
          :workspaceSubmissionStats {:runningSubmissionsCount (rand-int 2)
                                     :lastSuccessDate (rand-nth [nil (utils/rand-recent-time)])
                                     :lastFailureDate (rand-nth [nil (utils/rand-recent-time)])}
          :owners (utils/rand-subset ["test@broadinstitute.org"
                                      "test2@broadinstitute.org"
                                      "you@broadinstitute.org"])}))
     (range (rand-int 100)))})

(defn create-workspace [namespace name]
  {:path "/workspaces"
   :method :post
   :mock-data
   {:namespace namespace
    :name name
    :status (rand-nth ["Complete" "Running" "Exception"])
    :createdBy name
    :createdDate (.toISOString (js/Date.))}})

(defn get-workspace [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id))
   :method :get
   :mock-data
   {:accessLevel "OWNER"
    :workspace {:namespace (:namespace workspace-id)
                :name (:name workspace-id)
                :status (rand-nth ["Complete" "Running" "Exception"])
                :createdBy (:namespace workspace-id)
                :createdDate (.toISOString (js/Date.))}
    :workspaceSubmissionStats {:runningSubmissionsCount (rand-int 2)
                               :lastSuccessDate (rand-nth [nil (utils/rand-recent-time)])
                               :lastFailureDate (rand-nth [nil (utils/rand-recent-time)])}
    :owners ["test@broadinstitute.org"]}})

(defn delete-workspace [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id))
   :method :delete})

(defn get-workspace-acl [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/acl")
   :method :get
   :mock-data
   {:acl (into {} (map (fn [i] [(str "user" i "@broadinstitute.org")
                                (rand-nth ["OWNER" "WRITER" "READER"])])
                    (range (inc (rand-int 5)))))}})

(defn update-workspace-acl [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/acl")
   :method :patch})


(defn list-workspace-method-configs [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/methodconfigs")
   :method :get
   :mock-data
   (map
     (fn [i]
       {:name (str "Configuration " (inc i))
        :namespace (rand-nth ["Broad" "nci" "public"])
        :rootEntityType "Task"
        :methodStoreMethod {:methodNamespace (str "ms_ns_" (inc i))
                            :methodName (str "ms_n_" (inc i))
                            :methodVersion (str "ms_v_" (inc i))}
        :methodStoreConfig {:methodConfigNamespace (str "msc_ns_" (inc i))
                            :methodConfigName (str "msc_n_" (inc i))
                            :methodConfigVersion (str "msc_v_" (inc i))}
        ;; Real data doesn't have the following fields, but for mock data we carry the same
        ;; objects around, so initialize them here for convenience
        :inputs {"Input 1" "[some value]"
                 "Input 2" "[some value]"}
        :outputs {"Output 1" "[some value]"
                  "Output 2" "[some value]"}
        :prerequisites {"unused 1" "Predicate 1"
                        "unused 2" "Predicate 2"}})
     (range (rand-int 50)))})

(defn post-workspace-method-config [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/methodconfigs")
   :method :post})

(defn get-workspace-method-config [workspace-id config]
  {:path (str "/workspaces/" (ws-path workspace-id)
           "/method_configs/" (config "namespace") "/" (config "name"))
   :method :get
   :mock-data
   (assoc config "methodRepoMethod" (config "methodStoreMethod")
                 "methodRepoConfig" (config "methodStoreConfig"))})

(defn update-workspace-method-config [workspace-id config]
  {:path (str "/workspaces/" (ws-path workspace-id)
           "/method_configs/" (config "namespace") "/" (config "name"))
   :method :put})

(defn rename-workspace-method-config [workspace-id config]
  {:path (str "/workspaces/" (ws-path workspace-id)
           "/method_configs/" (config "namespace") "/" (config "name") "/rename")
   :method :post})

(defn delete-workspace-method-config [workspace-id config]
  {:path (str "/workspaces/" (ws-path workspace-id)
           "/method_configs/" (config "namespace") "/" (config "name"))
   :method :delete})

(defn import-entities [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/importEntities")
   :method :post
   :mock-data
   [{:entityName "foo" :entityType "bar" :succeeded false :message "ohno"}
    {:entityName "bar" :entityType "baz" :succeeded true :message "hooray"}]})

(defn get-entities-by-type [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/entities_with_type")
   :method :get
   :mock-data
   (map
     (fn [i]
       {:entityType (rand-nth ["sample" "participant"])
        :name (str "entity" (inc i))
        :attributes {}})
     (range (rand-int 20)))})


(defn list-submissions [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/submissions")
   :method :get
   :mock-data
   (map
     (fn [i]
       {:workspaceName workspace-id
        :methodConfigurationNamespace "my_test_configs"
        :submissionDate (utils/rand-recent-time)
        :submissionId "46bfd579-b1d7-4f92-aab0-e44dd092b52a"
        :notstarted []
        :workflows [{:messages []
                     :workspaceName workspace-id
                     :statusLastChangedDate (utils/rand-recent-time)
                     :workflowEntity {:entityType "sample"
                                      :entityName "sample_01"}
                     :status "Succeeded"
                     :workflowId "97adf170-ee40-40a5-9539-76b72802e124"}]
        :methodConfigurationName (str "test_config" (inc i))
        :status (rand-nth ["Submitted" "Done"])
        :submissionEntity {:entityType "sample"
                           :entityName (str "sample_" (inc i))}
        :submitter "abaumann@broadinstitute.org"})
     (range (rand-int 50)))})

(defn create-submission [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/submissions")
   :method :post
   :mock-data
   [{:workspaceName {:namespace "broad-dsde-dev"
                     :name "alexb_test_submission"}
     :methodConfigurationNamespace "my_test_configs"
     :submissionDate "2015-08-18T150715.393Z"
     :methodConfigurationName "test_config2"
     :submissionId "62363984-7b85-4f27-b9c6-7577561f1326"
     :notstarted []
     :workflows [{:messages []
                  :workspaceName {:namespace "broad-dsde-dev",
                                  :name "alexb_test_submission"}
                  :statusLastChangedDate "2015-08-18T150715.393Z"
                  :workflowEntity {:entityType "sample",
                                   :entityName "sample_01"}
                  :status "Submitted"
                  :workflowId "70521329-88fe-4288-9325-2e6183e0a9dc"}]
     :status "Submitted"
     :submissionEntity {"entityType" "sample",
                        "entityName" "sample_01"}
     :submitter "davidan@broadinstitute.org"}]})

(defn get-submission [workspace-id submission-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/submissions/" submission-id)
   :method :get
   :mock-data
   {:submissionId submission-id
    :submissionDate (utils/rand-recent-time)
    :submitter "test@broadinstitute.org"
    :methodConfigurationNamespace "broad-dsde-dev"
    :methodConfigurationName "some method conf"
    :submissionEntity {:entityType (rand-nth ["sample" "participant"])
                       :entityName "foo"}
    :workflows (map (fn [i]
                      {:messages []
                       :workspaceName "foo"
                       :statusLastChangedDate (utils/rand-recent-time)
                       :workflowEntity {:entityType "sample"
                                        :entityName (str "sample_" i)}
                       :status (rand-nth ["Succeeded" "Submitted" "Running" "Failed" "Aborted" "Unknown"])
                       :workflowId "97adf170-ee40-40a5-9539-76b72802e124"})
                 (range (rand-int 10)))
    :notstarted (map (fn [i]
                       {:entityType (rand-nth ["Sample" "Participant"])
                        :entityName (str "entity " i)
                        :errors (utils/rand-subset ["Prerequisites not met" "Server error"
                                                    "I didn't feel like it" "Syntax error"])})
                  (range (rand-int 5)))
    :status (rand-nth ["Submitted" "Done"])}})

(defn abort-submission [workspace-id submission-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/submissions/" submission-id)
   :method :delete})


(def list-methods
  {:path "/methods"
   :method :get
   :mock-data
   (map
     (fn [i]
       {:namespace (rand-nth ["broad" "public" "nci"])
        :name (str "Method " (inc i))
        :snapshotId (rand-int 100)
        :synopsis (str (rand-nth ["variant caller synopsis", "gene analyzer synopsis", "mutect synopsis"]) " " (inc i))
        :documentation (str "Documentation for method " (inc i))
        :createDate (utils/rand-recent-time)
        :url "http://agora.broadinstitute.org/methods/someurl"
        :payload "task wc {File in_file command { cat ${in_file} | wc -l } output { Int count = read_int(stdout()) }}\n"
        :entityType (rand-nth ["Task" "Workflow"])})
     (range (rand-int 100)))})


(def list-configurations
  {:path "/configurations"
   :method :get
   :mock-data
   (map
     (fn [i]
       {:method {:namespace (rand-nth ["broad" "public" "nci"])
                 :name (str "Method " (rand-int 100))
                 :snapshotId (rand-int 100)
                 :synopsis (str (rand-nth ["variant caller synopsis", "gene analyzer synopsis", "mutect synopsis"]) " " (inc i))
                 :documentation (str "Documentation for method " (inc i))
                 :createDate (utils/rand-recent-time)
                 :url "http://agora.broadinstitute.org/methods/someurl"
                 :payload "task wc {File in_file command { cat ${in_file} | wc -l } output { Int count = read_int(stdout()) }}\n"
                 :entityType (rand-nth ["Task" "Workflow"])}
        :namespace (rand-nth ["Broad" "nci" "public" "ISB"])
        :name (str "Configuration " (inc i))
        :snapshotId (rand-int 100)
        :synopsis (str (rand-nth ["variant caller synopsis", "gene analyzer synopsis", "mutect synopsis"]) " " (inc i))
        :documentation (str "Documentation for config " (inc i))
        :createDate (utils/rand-recent-time)
        :payload "task wc {File in_file command { cat ${in_file} | wc -l } output { Int count = read_int(stdout()) }}\n"
        :entityType "Task"})
     (range (rand-int 50)))})

(defn get-configuration [namespace name snapshot-id]
  {:path (str "/configurations/" namespace "/" name "/" snapshot-id)
   :method :get
   :mock-data
   {:method {:namespace (rand-nth ["broad" "public" "nci"])
             :name (str "Method " (rand-int 100))
             :snapshotId (rand-int 100)
             :synopsis (rand-nth ["variant caller synopsis", "gene analyzer synopsis", "mutect synopsis"])
             :documentation (str "Documentation for method")
             :createDate (utils/rand-recent-time)
             :url "http://agora.broadinstitute.org/methods/someurl"
             :payload "task wc {File in_file command { cat ${in_file} | wc -l } output { Int count = read_int(stdout()) }}\n"
             :entityType (rand-nth ["Task" "Workflow"])}
    :namespace namespace
    :name name
    :snapshotId snapshot-id
    :synopsis (rand-nth ["variant caller synopsis", "gene analyzer synopsis", "mutect synopsis"])
    :documentation (str "Documentation for config")
    :createDate (utils/rand-recent-time)
    :payload "task wc {File in_file command { cat ${in_file} | wc -l } output { Int count = read_int(stdout()) }}\n"
    :entityType "Task"}})

(defn copy-method-config-to-workspace [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/method_configs/copyFromMethodRepo")
   :method :post
   :mock-data
   {:name "Mock Configuration"
    :url (str "http://agora-ci.broadinstitute.org/configurations/joel_test/jt_test_config/1")
    :namespace (rand-nth ["Broad" "nci" "public" "ISB"])
    :snapshotId (rand-int 100)
    :synopsis (rand-nth ["variant caller synopsis", "gene analyzer synopsis", "mutect synopsis"])
    :createDate (utils/rand-recent-time)
    :owner (rand-nth ["thibault@broadinstitute.org" "esalinas@broadinstitute.org"])}})

(defn copy-method-config-to-repo [workspace-id config]
      {:path (str "/workspaces/" (ws-path workspace-id)
                  "/method_configs/copyToMethodRepo")
       :method :post})

(defn create-template [method]
  {:path "/template"
   :method :post
   :mock-data
   {:namespace (method "namespace")
    :name (method "name")
    :rootEntityType (method "rootEntityType")
    :prerequisites {"unused" "Some prereq"}
    :inputs {"input1" "val1"}
    :outputs {"output1" "val2"}
    :methodRepoConfig {:methodConfigNamespace (method "namespace")
                       :methodConfigName (method "name")
                       :methodConfigVersion 1}
    :methodRepoMethod {:methodNamespace (method "namespace")
                       :methodName (method "name")
                       :methodVersion 1}}})
