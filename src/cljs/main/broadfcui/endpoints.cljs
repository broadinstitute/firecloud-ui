(ns broadfcui.endpoints
  (:require
    [broadfcui.common :as common]
    [broadfcui.utils :as utils]
    ))


(defn- rand-recent-time []
  (common/format-date (- (.getTime (js/Date.)) (rand-int 100000000))))

(defn call-ajax-orch [{:keys [endpoint] :as arg-map}]
  (utils/ajax-orch
    (:path endpoint)
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
       (let [ns (rand-nth ["broad" "public" "nci"])]
         {:accessLevel (rand-nth ["OWNER" "WRITER" "READER"])
          :workspace {:workspaceId "ce601ccd-f6d5-40ac-ad2b-89beca5c4053"
                      :name (str "Workspace " (inc i))
                      :isLocked (> (rand) 0.8)
                      :attributes {"Attribute1" "[some value]"
                                   "Attribute2" "[some value]"
                                   "Attribute3" "[some value]"
                                   "description" (str "This is a test workspace " i)}
                      :createdBy "somebody@broadinstitute.org"
                      :bucketName "unavailable"
                      :namespace ns
                      :createdDate (rand-recent-time)}
          :workspaceSubmissionStats {:runningSubmissionsCount (rand-int 2)
                                     :lastSuccessDate (rand-nth [nil (rand-recent-time)])
                                     :lastFailureDate (rand-nth [nil (rand-recent-time)])}
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
    :workspace {:workspaceId "ce601ccd-f6d5-40ac-ad2b-89beca5c4053"
                :name (:name workspace-id)
                :isLocked (> (rand) 0.8)
                :attributes {"Attribute1" "[some value]"
                             "Attribute2" "[some value]"
                             "Attribute3" "[some value]"
                             "description" "This is a test workspace"}
                :createdBy "somebody@broadinstitute.org"
                :bucketName (rand-nth ["unavailable" "bucket-12345" "bucket-67890"])
                :namespace (:namespace workspace-id)
                :createdDate (.toISOString (js/Date.))}
    :workspaceSubmissionStats {:runningSubmissionsCount (rand-int 2)
                               :lastSuccessDate (rand-nth [nil (rand-recent-time)])
                               :lastFailureDate (rand-nth [nil (rand-recent-time)])}
    :owners (utils/rand-subset ["test@broadinstitute.org"
                                "test2@broadinstitute.org"
                                "you@broadinstitute.org"])}})

(defn delete-workspace [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id))
   :method :delete})

(defn get-workspace-acl [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/acl")
   :method :get
   :mock-data
   (into {} (map (fn [i] [(str "user" i "@broadinstitute.org")
                                (rand-nth ["OWNER" "WRITER" "READER"])])
                    (range (inc (rand-int 5)))))})

(defn check-bucket-read-access [workspace-id]
      {:path (str "/workspaces/" (ws-path workspace-id) "/checkBucketReadAccess")
       :method :get})

(defn update-workspace-acl [workspace-id invite-new?]
  {:path (str "/workspaces/" (ws-path workspace-id) "/acl?inviteUsersNotFound=" (true? invite-new?))
   :method :patch})

(defn clone-workspace [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/clone")
   :method :post})

(defn lock-or-unlock-workspace [workspace-id locked-now?]
  {:path (str "/workspaces/" (ws-path workspace-id) (if locked-now? "/unlock" "/lock"))
   :method :put})


(defn list-workspace-method-configs [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/methodconfigs")
   :method :get
   :mock-data
   (map
     (fn [i]
       {:name (str "Configuration " (inc i))
        :namespace (rand-nth ["Broad" "nci" "public"])
        :rootEntityType (rand-nth ["sample" "participant"])
        :methodRepoMethod {:methodNamespace (rand-nth ["Broad" "nci" "public"])
                           :methodName (rand-nth ["foo" "bar" "baz"])
                           :methodVersion (rand-int 50)}
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
   (assoc config "methodRepoMethod" (config "methodStoreMethod"))})

(defn update-workspace-method-config [workspace-id config]
  {:path (str "/workspaces/" (ws-path workspace-id)
           "/method_configs/" (config "namespace") "/" (config "name"))
   :method :put
   :mock-data
   {:methodConfiguration {:name (str (config "name"))
                          :namespace (rand-nth ["Broad" "nci" "public"])
                          :rootEntityType (rand-nth ["sample" "participant"])
                          :methodRepoMethod {:methodNamespace (str (config "namespace"))
                                             :methodName (str (config "name"))
                                             :methodVersion (str "ms_v_1")}
                          :methodStoreConfig {:methodConfigNamespace (str (config "namespace"))
                                              :methodConfigName (str (config "name"))
                                              :methodConfigVersion (str "msc_v_1")}
                          ;; Real data doesn't have the following fields, but for mock data we carry the same
                          ;; objects around, so initialize them here for convenience
                          :inputs {"Input 1" "workspace.foo"
                                   "Input 2" "workspace.baz"
                                   "Input 3" "workspace.f00"}
                          :outputs {"Output 1" "workspace.bla"
                                    "Output 2" "workspace.bar"
                                    "Output 3" "workspace.f0o"}
                          :prerequisites {"unused 1" "Predicate 1"
                                          "unused 2" "Predicate 2"}}}})

(defn delete-workspace-method-config [workspace-id config]
  {:path (str "/workspaces/" (ws-path workspace-id)
           "/method_configs/" (config "namespace") "/" (config "name"))
   :method :delete})

(defn get-validated-workspace-method-config [workspace-id config-id]
  {:path (str "/workspaces/" (ws-path workspace-id)
           "/method_configs/" (:namespace config-id) "/" (:name config-id) "/validate")
   :method :get
   :mock-data
   {:methodConfiguration {:name (:name config-id)
                          :namespace (rand-nth ["Broad" "nci" "public"])
                          :rootEntityType (rand-nth ["sample" "participant"])
                          :methodRepoMethod {:methodNamespace (:namespace config-id)
                                             :methodName (:name config-id)
                                             :methodVersion (str "ms_v_1")}
                          :methodStoreConfig {:methodConfigNamespace (:namespace config-id)
                                              :methodConfigName (:name config-id)
                                              :methodConfigVersion (str "msc_v_1")}
                          ;; Real data doesn't have the following fields, but for mock data we carry the same
                          ;; objects around, so initialize them here for convenience
                          :inputs {"Input 1" "workspace.foo"
                                   "Input 2" "workspace.baz"
                                   "Input 3" "workspace.f00"}
                          :outputs {"Output 1" "workspace.bla"
                                    "Output 2" "workspace.bar"
                                    "Output 3" "workspace.f0o"}
                          :prerequisites {"unused 1" "Predicate 1"
                                          "unused 2" "Predicate 2"}}
    :invalidInputs (rand-nth [{"Input 1" "Failed at line 1, column 1: `workspace.' expected but `t' found"}
                              {"Input 2" "Failed at line 1, column 1: `workspace.' expected but `t' found"}
                              {"Input 3" "Failed at line 1, column 1: `workspace.' expected but `t' found"}])
    :invalidOutputs (rand-nth [{"Output 1" "Failed at line 1, column 1: `workspace.' expected but `t' found"}
                               {"Output 2" "Failed at line 1, column 1: `workspace.' expected but `t' found"}
                               {"Output 3" "Failed at line 1, column 1: `workspace.' expected but `t' found"}])}})

(defn update-workspace-attrs [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/updateAttributes")
   :method :patch})

(defn set-workspace-attributes [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/setAttributes")
   :method :patch})

(defn import-entities [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/importEntities")
   :method :post
   :mock-data
   [{:entityName "foo" :entityType "bar" :succeeded false :message "ohno"}
    {:entityName "bar" :entityType "baz" :succeeded true :message "hooray"}]})

(defn import-attributes [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/importAttributesTSV")
   :method :post})

(defn get-entity-types [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/entities")
   :method :get
   :mock-data {"participant" (rand-int 100)
               "sample" (rand-int 100)
               "pair" (rand-int 10)}})

(defn get-entities-of-type [workspace-id type]
  {:path (str "/workspaces/" (ws-path workspace-id) "/entities/" type)
   :method :get
   :mock-data
   (map (fn [i]
          {:entityType type
           :name (str "entity" (inc i))
           :attributes {}})
     (range (rand-int 20)))})

(defn get-entity [workspace-id type entity-name]
  {:path (str "/workspaces/" (ws-path workspace-id) "/entities/" type "/" entity-name)
   :method :get
   :mock-data
     {:entityType "sample"
        :name "HCC_4532"
        :attributes {:participant {:entityType "participant" :entityName (str "subject_" (rand-int 1000))}}
        :sample_type "tumor"
        :ref_fasta "gs://cancer-exome-pipeline-demo-data/Homo_sapiens_assembly19.fasta"
        :ref_dict "gs://cancer-exome-pipeline-demo-data/Homo_sapiens_assembly19.dict"
        :ref_intervals "gs://cancer-exome-pipeline-demo-data/panel_100_genes.interval_list"
        :ref_ann "gs://cancer-exome-pipeline-demo-data/Homo_sapiens_assembly19.fasta.64.ann"
        :dna "gs://fc-d1f53ee4-4abe-4de0-91b2-597a609deddc/inDNA"
        :ref_sa "gs://cancer-exome-pipeline-demo-data/Homo_sapiens_assembly19.fasta.64.sa"
        :fastq2 "gs://cancer-exome-pipeline-demo-data/HCC1143.100_gene_250bp_pad_2.fastq"
        :library "test_library"
        :strip_unpaired "TRUE"
        :sample "HCC_4532"
        :ref_bwt "gs://cancer-exome-pipeline-demo-data/Homo_sapiens_assembly19.fasta.64.bwt"
        :platform "illumina"
        :bai "gs://cancer-exome-pipeline-demo-data/HCC1143.100_gene_250bp_pad.bai"
        :bam "gs://cancer-exome-pipeline-demo-data/HCC1143.100_gene_250bp_pad.bam"
        :ref_pac "gs://cancer-exome-pipeline-demo-data/Homo_sapiens_assembly19.fasta.64.pac"}})

(defn get-entities-paginated [workspace-id type query-parameters]
  (assert (= #{"page" "pageSize" "sortField" "sortDirection" "filterTerms"}
             (set (keys query-parameters)))
          (str "Malformed query parameters: " query-parameters))
  {:path (str "/workspaces/" (ws-path workspace-id) "/entityQuery/" type "?"
              (clojure.string/join "&" (keep (fn [[k v]]
                                               (some->> v str not-empty (str k "=")))
                                             query-parameters)))
   :method :get
   :mock-data
   (let [{:strs [page pageSize]} query-parameters]
     {:parameters query-parameters
      :resultMetadata {:unfilteredCount 5678
                       :filteredCount 1234
                       :filteredPageCount (int (/ 1234 pageSize))}
      :results (map (fn [i]
                      {:entityType type
                       :name (str "entity" (inc i))
                       :attributes {}})
                    (map #(-> % (+ 1 (* (dec page) pageSize)))
                         (range pageSize)))})})

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

(defn delete-entities [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/entities/delete")
   :method :post})


(defn list-submissions [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/submissions")
   :method :get
   :mock-data
   (map
     (fn [i]
       {:workspaceName workspace-id
        :methodConfigurationNamespace (rand-nth ["Broad" "nci" "public"])
        :methodConfigurationName (str "test_config" (inc i))
        :submissionDate (rand-recent-time)
        :submissionId "46bfd579-b1d7-4f92-aab0-e44dd092b52a"
        :notstarted []
        :workflows [{:messages []
                     :workspaceName workspace-id
                     :statusLastChangedDate (rand-recent-time)
                     :workflowEntity {:entityType "sample"
                                      :entityName "sample_01"}
                     :status "Succeeded"
                     :workflowId "97adf170-ee40-40a5-9539-76b72802e124"}]
        :status (rand-nth ["Accepted" "Evaluating" "Submitting" "Submitted" "Done"])
        :submissionEntity {:entityType "sample"
                           :entityName (str "sample_" (inc i))}
        :submitter "abaumann@broadinstitute.org"})
     (range (rand-int 50)))})

(defn count-submissions [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/submissionsCount")
   :method :get
   :mock-data
   [{:Done (rand-int 10)
     :Submitted (rand-int 10)
     :Aborted (rand-int 10)
     :Aborting (rand-int 10)}]})

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
    :submissionDate (rand-recent-time)
    :submitter "test@broadinstitute.org"
    :methodConfigurationNamespace "broad-dsde-dev"
    :methodConfigurationName "some method conf"
    :submissionEntity {:entityType (rand-nth ["sample" "participant"])
                       :entityName "foo"}
    :workflows (map (fn [i]
                      {:messages []
                       :workspaceName "foo"
                       :statusLastChangedDate (rand-recent-time)
                       :workflowEntity {:entityType "sample"
                                        :entityName (str "sample_" i)}
                       :status (rand-nth ["Succeeded" "Submitted" "Running" "Launching" "Queued" "Aborting" "Failed" "Aborted" "Unknown"])
                       :workflowId "97adf170-ee40-40a5-9539-76b72802e124"})
                 (range (rand-int 10)))
    :notstarted (map (fn [i]
                       {:entityType (rand-nth ["Sample" "Participant"])
                        :entityName (str "entity " i)
                        :inputResolutions [{"value" "gs://cancer-exome-pipeline-demo-data/panel_100_genes.interval_list",
                                            "inputName" (rand-nth ["input1" "input2"])}
                                           {"error" "Expected single value for workflow input, but evaluated result set was empty",
                                            "inputName" (rand-nth ["input1" "input2"])}
                                           {"value" "mutations.vcf",
                                            "inputName" (rand-nth ["input1" "input2"])}]
                        :errors (utils/rand-subset ["Prerequisites not met" "Server error"
                                                    "I didn't feel like it" "Syntax error"])})
                  (range (rand-int 5)))
    :status (rand-nth ["Accepted" "Evaluating" "Submitting" "Submitted" "Done"])}})

(defn get-workflow-details [workspace-id submission-id workflow-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/submissions/" submission-id
              "/workflows/" workflow-id)
   :method :get
   :mock-data
   {:calls {"CancerExomePipeline_v2.M2" [{"executionStatus" "Failed"
                                          "stdout" "gs://rawls-e7d5008e-bb56-4966-8580-be5004cb7a00/c4e8460c-a158-4498-ab64-b6c7c36498b2/CancerExomePipeline_v2/394d93f4-a150-45f2-b864-c35e97f9df6a/call-M2/job.stdout.txt"
                                          "backendStatus" "Success"
                                          "outputs" {}
                                          "inputs" {"ref_fasta" "gs://cancer-exome-pipeline-demo-data/Homo_sapiens_assembly19.fasta"
                                                    "tumor_bai" "gs://cancer-exome-pipeline-demo-data/HCC1143_BL.100_gene_250bp_pad.bai"
                                                    "tumor_bam" "gs://cancer-exome-pipeline-demo-data/HCC1143_BL.100_gene_250bp_pad.bam"
                                                    "intervals" "gs://cancer-exome-pipeline-demo-data/panel_100_genes.interval_list"
                                                    "m2_output_vcf_name" "mutations.vcf"
                                                    "ref_fasta_fai" "gs://cancer-exome-pipeline-demo-data/Homo_sapiens_assembly19.fasta.fai"
                                                    "normal_bam" "gs://cancer-exome-pipeline-demo-data/HCC1143.100_gene_250bp_pad.bam"
                                                    "normal_bai" "gs://cancer-exome-pipeline-demo-data/HCC1143.100_gene_250bp_pad.bai"
                                                    "ref_fasta_dict" "gs://cancer-exome-pipeline-demo-data/Homo_sapiens_assembly19.dict"}
                                          "jobId" "Some(operations/EM7aqseEKhix2bqUu638rPYBIMO73rS7FyoKcHJvZHVjdGlvbg)"
                                          "backend" "JES"
                                          "end" "2015-10-08T19:30:16.000Z"
                                          "stderr" "gs://rawls-e7d5008e-bb56-4966-8580-be5004cb7a00/c4e8460c-a158-4498-ab64-b6c7c36498b2/CancerExomePipeline_v2/394d93f4-a150-45f2-b864-c35e97f9df6a/call-M2/job.stderr.txt"
                                          "start" "2015-10-08T19:25:50.000Z"}]}
    :outputs {}
    :id workflow-id
    :inputs {"CancerExomePipeline_v2.M2.intervals" "gs://cancer-exome-pipeline-demo-data/panel_100_genes.interval_list"
             "CancerExomePipeline_v2.M2.tumor_bam" "gs://cancer-exome-pipeline-demo-data/HCC1143_BL.100_gene_250bp_pad.bam"
             "CancerExomePipeline_v2.M2.normal_bam" "gs://cancer-exome-pipeline-demo-data/HCC1143.100_gene_250bp_pad.bam"
             "CancerExomePipeline_v2.M2.ref_fasta_dict" "gs://cancer-exome-pipeline-demo-data/Homo_sapiens_assembly19.dict"
             "CancerExomePipeline_v2.M2.tumor_bai" "gs://cancer-exome-pipeline-demo-data/HCC1143_BL.100_gene_250bp_pad.bai"
             "CancerExomePipeline_v2.M2.normal_bai" "gs://cancer-exome-pipeline-demo-data/HCC1143.100_gene_250bp_pad.bai"
             "CancerExomePipeline_v2.M2.ref_fasta" "gs://cancer-exome-pipeline-demo-data/Homo_sapiens_assembly19.fasta"
             "CancerExomePipeline_v2.M2.ref_fasta_fai" "gs://cancer-exome-pipeline-demo-data/Homo_sapiens_assembly19.fasta.fai"
             "CancerExomePipeline_v2.M2.m2_output_vcf_name" "mutations.vcf"}
    :submission (rand-recent-time)
    :status (rand-nth ["Succeeded" "Submitted" "Running" "Aborting" "Failed" "Aborted" "Unknown"])
    :start (rand-recent-time)
    :end (rand-nth [(rand-recent-time) nil])}})

(defn abort-submission [workspace-id submission-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/submissions/" submission-id)
   :method :delete})

(defn storage-cost-estimate [workspace-id]
  {:path (str "/workspaces/" (ws-path workspace-id) "/storageCostEstimate")
   :method :get})


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
        :createDate (rand-recent-time)
        :url "http://agora.broadinstitute.org/methods/someurl"
        :payload "task wc {File in_file command { cat ${in_file} | wc -l } output { Int count = read_int(stdout()) }}\n"
        :entityType (rand-nth ["Task" "Workflow"])})
     (range (rand-int 100)))})

(def post-method
  {:path "/methods"
   :method :post})

;; I would have called it "get-method" but that's already defined in core
(defn get-agora-method [namespace name snapshot-id]
  {:path (str "/methods/" namespace "/" name "/" snapshot-id)
   :method :get
   :mock-data
   {:namespace namespace
    :name name
    :snapshotId snapshot-id
    :synopsis (rand-nth ["variant caller synopsis", "gene analyzer synopsis", "mutect synopsis"])
    :documentation (str "Documentation for method")
    :createDate (rand-recent-time)
    :url "http://agora.broadinstitute.org/methods/someurl"
    :payload "task wc {File in_file command { cat ${in_file} | wc -l } output { Int count = read_int(stdout()) }}\n"
    :entityType (rand-nth ["Task" "Workflow"])}})


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
                 :createDate (rand-recent-time)
                 :url "http://agora.broadinstitute.org/methods/someurl"
                 :payload "task wc {File in_file command { cat ${in_file} | wc -l } output { Int count = read_int(stdout()) }}\n"
                 :entityType (rand-nth ["Task" "Workflow"])}
        :namespace (rand-nth ["Broad" "nci" "public" "ISB"])
        :name (str "Configuration " (inc i))
        :snapshotId (rand-int 100)
        :synopsis (str (rand-nth ["variant caller synopsis", "gene analyzer synopsis", "mutect synopsis"]) " " (inc i))
        :documentation (str "Documentation for config " (inc i))
        :createDate (rand-recent-time)
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
             :createDate (rand-recent-time)
             :url "http://agora.broadinstitute.org/methods/someurl"
             :payload "task wc {File in_file command { cat ${in_file} | wc -l } output { Int count = read_int(stdout()) }}\n"
             :entityType (rand-nth ["Task" "Workflow"])}
    :namespace namespace
    :name name
    :snapshotId snapshot-id
    :synopsis (rand-nth ["variant caller synopsis", "gene analyzer synopsis", "mutect synopsis"])
    :documentation (str "Documentation for config")
    :createDate (rand-recent-time)
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
    :createDate (rand-recent-time)
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
    :methodRepoMethod {:methodNamespace (method "namespace")
                       :methodName (method "name")
                       :methodVersion 1}}})

(def get-inputs-outputs
  {:path "/inputsOutputs"
   :method :post
   :mock-data
   {:inputs [{:name "CancerExomePipeline_v2.M2.ref_fasta"
              :inputType "File"
              :optional false}
             {:name "CancerExomePipeline_v2.M2.ref_fasta_dict"
              :inputType "File"
              :optional false}
             {:name "CancerExomePipeline_v2.M2.ref_fasta_fai"
              :inputType "File"
              :optional false}
             {:name "CancerExomePipeline_v2.M2.m2_output_vcf_name"
              :inputType "String"
              :optional true}]
    :outputs [{:name "CancerExomePipeline_v2.M2.m2_output_vcf"
               :outputType "File"}]}})

(defn get-agora-method-acl [ns n sid is-conf]
  {:path (str "/" (if is-conf "configurations" "methods"  ) "/" ns "/" n "/" sid "/permissions"  )
   :method :get
   :mock-data
   (let [random-data (map (fn [i] {:user (str "user" i "@broadinstitute.org")
                                   :role (rand-nth ["READER" "OWNER" "NO ACCESS"])})
                       (range (inc (rand-int 5))))
         add-public? (rand-nth [true false])
         public-value (rand-nth ["READER" "NO ACCESS"])
         public-map {:user "public" :role public-value}]
     (if add-public?
       (flatten [public-map random-data])
       random-data))})

(defn get-agora-namespace-acl [namespace is-conf?]
  {:path (str "/" (if is-conf? "configurations" "methods" ) "/" namespace "/permissions")
   :method :get})

(defn post-agora-namespace-acl [namespace is-conf?]
  {:path (str "/" (if is-conf? "configurations" "methods" ) "/" namespace "/permissions")
   :method :post})


(defn delete-agora-entity [config? ns n sid]
  {:path (let [base (if config? "configurations" "methods")]
           (str "/" base "/" ns "/" n "/" sid))
   :method :delete})


(defn persist-agora-method-acl [ent]
  {:path (let [ent-type (ent "entityType")
               name (ent "name")
               nmsp (ent "namespace")
               sid (ent "snapshotId")
               base (cond
                      (= "Configuration" ent-type) "configurations"
                      (or (= "Task" ent-type) (= "Workflow" ent-type)) "methods"
                      :else (do
                              (utils/log "Error, unknown type : " ent-type)
                              (str "configurations")))]
           (str "/" base "/" nmsp "/" name "/" sid "/permissions"))
   :method :post})


(defn copy-entity-to-workspace [workspace-id re-link-soft-conflicts?]
  {:path (str "/workspaces/" (ws-path workspace-id)
              "/entities/copy?linkExistingEntities=" (boolean re-link-soft-conflicts?))
   :method :post})


(defn get-gcs-stats [bucket object]
  {:path (str "/storage/" bucket "/" object)
   :method :get
   :mock-data
   (let [generation (rand-int 2000000000000000)]
     {:kind "storage#object"
      :id (str bucket "/" object "/" generation)
      :selfLink (str "https://www.googleapis.com/storage/v1/b/" bucket "/o/" object)
      :name object
      :bucket bucket
      :generation generation
      :metageneration (rand-int 5)
      :contentType "application/octet-stream"
      :timeCreated (rand-recent-time)
      :updated (rand-recent-time)
      :storageClass "STANDARD"
      :size (rand-int 1000000000)
      :md5Hash "wVDkfF0kkuCJPazkScLgzQ=="
      :mediaLink (str "https://www.googleapis.com/download/storage/v1/b/" bucket
                   "/o/" name "?generation=" generation "&alt=media")
      :contentLanguage "en"
      :owner {:entity "user-00b4903a972fe36b39dff1717b25449fb5d31ca67369e9a60cb0dc4590461a0d"
              :entityId "00b4903a972fe36b39dff1717b25449fb5d31ca67369e9a60cb0dc4590461a0d"}
      :crc32c "avsUEw=="
      :etag "CNjyodHyuscCEAI="})})


(defn profile-get
  ([on-done]
   (utils/ajax-orch
     "/profile"
     {:on-done on-done
      :canned-response
      {:status 200 :delay-ms (rand-int 2000)
       :responseText
       (utils/->json-string
         {:userId "55"
          :keyValuePairs
          (map (fn [[k v]] {:key k :value v})
               {:isRegistrationComplete "1" :name "John Doe" :email "jdoe@example.com"
                :googleProjectIds ["14" "7"] :institution "Broad Institute"
                :pi "Jane Doe"})})}}
     :service-prefix "/register")))


(defn profile-set [payload on-done]
  (utils/ajax-orch
    "/profile"
    {:method :post
     :data (utils/->json-string payload)
     :on-done on-done
     :headers utils/content-type=json
     :canned-response {:status (rand-nth [200 200 500]) :delay-ms (rand-int 2000)}}
    :service-prefix "/register"))


(defn profile-get-nih-status [on-done]
  (utils/ajax-orch
   "/nih/status"
   {:on-done on-done}))


(defn profile-link-nih-account [token on-done]
  (utils/ajax-orch
   "/nih/callback"
   {:method :post
    :data (utils/->json-string {:jwt token})
    :on-done on-done
    :headers utils/content-type=json
    :canned-response {:status 200 :delay-ms (rand-int 2000)}}))


(defn get-groups [on-done]
  (call-ajax-orch
   {:endpoint {:path "/groups"
               :method :get}
    :on-done (fn [{:keys [success? status-text get-parsed-response]}]
               (if success?
                 (on-done nil (get-parsed-response))
                 (on-done status-text nil)))}))


(defn get-billing-projects
  ([on-done] (get-billing-projects false on-done))
  ([include-pending? on-done]
   (call-ajax-orch
    {:endpoint {:path "/profile/billing"
                :method :get
                :mock-data (utils/rand-subset
                            [{"projectName" "broad-dsde-dev" "role" "User"
                              "creationStatus" "Ready"}
                             {"projectName" "broad-institute" "role" "User"
                              "creationStatus" "Ready"}])}
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
   :method :get
   :mock-data
   [{:accountName "billingAccounts/00473A-04A1D8-155CAB"
     :firecloudHasAccess false}
    {:accountName "billingAccounts/foo-bar-baz"
     :firecloudHasAccess true}]})

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
  (utils/ajax-orch
   "/library/groups"
   {:method :get
    :on-done on-done}))

(defn get-library-attributes [on-done]
  (utils/ajax-orch
    "/library-attributedefinitions-v1"
    {:method :get
     :on-done on-done}
    :service-prefix "/schemas"))

(defn get-consent [orsp-id on-done]
  (utils/ajax-orch
    (str "/duos/consent/orsp/" (js/encodeURIComponent orsp-id))
    {:method :get
     :on-done on-done}))

(defn save-library-metadata [workspace-id]
  {:path (str "/library/" (ws-path workspace-id) "/metadata")
   :method :put})

(defn save-discoverable-by-groups [workspace-id]
  {:path (str "/library/" (ws-path workspace-id) "/discoverableGroups")
   :method :put})

(defn publish-workspace [workspace-id]
  {:path (str "/library/" (ws-path workspace-id) "/published")
   :method :post})

(defn unpublish-workspace [workspace-id]
  {:path (str "/library/" (ws-path workspace-id) "/published")
   :method :delete})

(def get-library-curator-status
  {:path "/library/user/role/curator"
   :method :get
   :mock-data {:curator true}})

(def search-datasets
  {:path "/library/search"
   :method :post })

(defn submissions-queue-status []
  {:path "/submissions/queueStatus"
   :method :get
   :mock-data {"estimatedQueueTimeMS" (rand-int 10000000)
               "workflowCountsByStatus"
               {"Queued" (rand-int 10000)
                "Launching" (rand-int 10000)
                "Submitted" (rand-int 10000)
                "Running" (rand-int 1000)
                "Aborting" (rand-int 1000)
                "Failed" (rand-int 1000)
                "Succeeded" (rand-int 1000)
                "Aborted" (rand-int 1000)
                "Unknown" (rand-int 1000)}}})

(defn cromwell-version []
  {:path "/version/executionEngine"
   :method :get
   :mock-data {"cromwell" "25-489f66b"}})
