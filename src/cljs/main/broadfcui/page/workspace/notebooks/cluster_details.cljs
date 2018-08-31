(ns broadfcui.page.workspace.notebooks.cluster-details
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.foundation-tooltip :refer [FoundationTooltip]]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.page.workspace.notebooks.utils :as notebook-utils]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   ))

(defn- machine-type-cpus [machine-type]
  (last (clojure.string/split machine-type #"-")))

(defn- machine-type-memory [machine-type]
  (case machine-type
    "n1-standard-1" 3.75
    "n1-standard-2" 7.5
    "n1-standard-4" 15
    "n1-standard-8" 30
    "n1-standard-16" 60
    "n1-standard-32" 120
    "n1-standard-64" 240
    "n1-standard-96" 360
    "n1-highmem-2" 13
    "n1-highmem-4" 26
    "n1-highmem-8" 52
    "n1-highmem-16" 104
    "n1-highmem-32" 208
    "n1-highmem-64" 416
    "n1-highmem-96" 624
    "n1-highcpu-2" 1.8
    "n1-highcpu-4" 3.6
    "n1-highcpu-8" 7.2
    "n1-highcpu-16" 14.4
    "n1-highcpu-32" 28.8
    "n1-highcpu-64" 57.6
    "n1-highcpu-96" 86.4))

(react/defc ClusterDetailsViewer
  {:component-did-mount
   (fn [{:keys [this]}]
     (this :-get-cluster-details))
   :render
   (fn [{:keys [state this props]}]
     (let [{:keys [server-response cluster-details]} @state
           {:keys [server-error]} server-response
           {:keys [dismiss]} props]
       [modals/OKCancelForm
        {:header "Cluster Details"
         :dismiss dismiss
         :ok-button {:text "Close" :onClick dismiss}
         :show-cancel? false
         :content
         (react/create-element
          [:div {:style {:width 700}}
           [comps/ErrorViewer {:error server-error}]
           (if cluster-details
             (this :-render-table)
             (spinner "Getting cluster details for cluster..."))])}]))

   :-render-table
   (fn [{:keys [state this props]}]
     (let [{:keys [cluster-details]} @state
           {:keys [machineConfig stagingBucket]} cluster-details]
       [:div {}
        (this :-render-row "Billing Project"
              "The Billing Project in which this cluster resides."
              (:googleProject cluster-details))
        (this :-render-row "Cluster Name"
              "The name of the cluster."
              (:clusterName cluster-details))
        (this :-render-row "Status"
              "Status of the cluster."
              (:status cluster-details))
        (this :-render-row "Created"
              "Creation date of the cluster."
              (common/format-date (:createdDate cluster-details)))
        (this :-render-row "Last Accessed"
              "Date the cluster was last accessed."
              (common/format-date (:dateAccessed cluster-details)))
        (this :-render-row "Staging Bucket"
              "Bucket containing data associated with the cluster, including initialization logs and Spark metadata."
              (links/create-external {:href (str moncommon/google-storage-context stagingBucket "/")
                                      :title "Click to open the Google Cloud Storage browser for this bucket"}
                stagingBucket))
        (this :-render-row "CPUs"
              "Number of CPUs on the cluster master node."
              (machine-type-cpus (:masterMachineType machineConfig)))
        (this :-render-row "Memory"
              "System memory on the cluster master node."
              (str (machine-type-memory (:masterMachineType machineConfig)) " GB"))
        (this :-render-row "Disk Size"
              "Size of the disk on the cluster master node."
              (str (:masterDiskSize machineConfig) " GB"))
        (when (not= (:numberOfWorkers machineConfig) 0)
          [:div {}
           (this :-render-row "Workers"
                 "Number of worker nodes in the Spark cluster."
                 (:numberOfWorkers machineConfig))
           (this :-render-row "Preemptibles"
                 "Number of preemptible worker nodes in the Spark cluster."
                 (:numberOfPreemptibleWorkers machineConfig))
           (this :-render-row "Worker CPUs"
                 "Number of CPUs on the cluster worker nodes."
                 (str (machine-type-cpus (:workerMachineType machineConfig)) " GB"))
           (this :-render-row "Worker Memory"
                 "System memory on the cluster worker nodes."
                 (str (machine-type-memory (:workerMachineType machineConfig)) " GB"))
           (this :-render-row "Worker Disk Size"
                 "Size of the disk on the cluster worker nodes."
                 (str (:workerDiskSize machineConfig) " GB"))])

        (when-let [filtered-labels (dissoc (:labels cluster-details) :clusterServiceAccount :clusterName :googleProject :googleBucket :creator)]
          (when (not-empty filtered-labels)
            (this :-render-row "Labels"
                  "User-specified labels for the cluster."
                  (map (fn [label]
                         [:div {:style {:display "flex"}}
                          [:span {:style {:backgroundColor (:tag-background style/colors)
                                          :borderTop style/standard-line :borderBottom style/standard-line
                                          :borderRadius 12 :marginRight 4 :padding "0.25rem 0.75rem"
                                          :fontSize "88%"}}
                           (if (clojure.string/blank? (val label))
                             (name (key label))
                             (str (name (key label)) " | " (val label) "\n\n"))]])
                       (into (sorted-map) filtered-labels)))))]))

   :-render-row
   (fn [{:keys [state this props]} key tooltip value]
     (flex/box {}
       [:div {:style {:width "20%" :marginRight "4%" :marginBotton "1%"}}
        [FoundationTooltip {:text (notebook-utils/create-inline-form-label key)
                            :tooltip tooltip}]]
       [:dev {:style {:width "80%" :marginBottom "1%"}}
        value]))

   :-get-cluster-details
   (fn [{:keys [state this props]}]
     (let [{:keys [cluster-to-display-details]} props]
       (utils/multi-swap! state (assoc :querying? true) (dissoc :server-error))
       (endpoints/call-ajax-leo
        {:endpoint (endpoints/get-cluster-details (get-in props [:workspace-id :namespace]) cluster-to-display-details)
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :querying?)
                    (if success?
                      (swap! state assoc :cluster-details (get-parsed-response))
                      (swap! state assoc :server-error (get-parsed-response false))))})))})
