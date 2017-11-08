(ns broadfcui.page.workspace.notebooks.notebooks
  (:require
   [clojure.set]
   [clojure.string :as string]
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.input :as input]
   [broadfcui.common.links :as links]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.utils :as utils]))



(defn render-spinner-icon []
  [:span {:style {:display "inline-flex" :alignItems "center" :justifyContent "center" :verticalAlign "middle"
                  :width table-style/table-icon-size :height table-style/table-icon-size
                  :borderRadius 3 :margin "-4px 4px 0 0"}
          :data-test-id "status-icon" :data-test-value "unknown"}
   [comps/Spinner {:size 12}]])

(defn icon-for-cluster-status [status]
  (case status
    ("Deleted", "Error") (moncommon/render-failure-icon)
    ("Creating", "Updating", "Deleting") (render-spinner-icon)
    "Running" (moncommon/render-success-icon)
    "Unknown" (moncommon/render-unknown-icon)))


(defn- create-section-header [text]
  [:div {:style {:fontSize "125%" :fontWeight 500}} text])

(defn- create-section [children]
  [:div {:style {:padding "1em 0 2em 0"}} children])


(react/defc- ClusterCreator
 {;:refresh render again?
  :get-initial-state
  (fn [{:keys [state]}]
    {:label-fields 0})
  :render
  (fn [{:keys [state this props]}]
    (let [{:keys [creating? server-error validation-errors]} @state]
      [modals/OKCancelForm
       {:header "Create Cluster"
        :dismiss (:dismiss props)
        :ok-button {:text "Create"
                    :onClick #(this :-create-cluster)}
        :content
        (react/create-element
         [:div {:style {:marginBottom -20}}
          (when creating? [comps/Blocker {:banner "Creating cluster..."}])
          (style/create-form-label "Name")
          [input/TextField {:ref "clusterNameCreate" :autoFocus true :style {:width "100%"}
                            :defaultValue ""
                            :predicates [(input/nonempty "Cluster name")
                                         (input/alphanumeric_- "Cluster name")]}]
          (style/create-form-label "Extension URI")
          [input/TextField {:ref "extensionURI" :autoFocus true :style {:width "100%"}
                            :predicates [(input/alphanumeric_- "Extension URI")]}]
          (style/create-form-label "Number of Workers")
          [input/TextField {:ref "numberOfWorkers" :autoFocus true :style {:width "100%"}
                            :predicates [(input/integer "Number of workers" true)]}]
          [:div {:display "inline-block" :width "50%" }
           (style/create-form-label "Master Machine Type")
           (style/create-form-label "Master Disk Size")]
          [:div {:display "inline-block" }
           ;(style/create-form-label "Master Machine Type")
           [input/TextField {:ref "masterMachineType" :autoFocus true :style {:width "48%" :marginRight "4%"}
                             :predicates [(input/alphanumeric_- "Master Machine Type")]}]
          ;(style/create-form-label "Master Disk Size")
          [input/TextField {:ref "masterDiskSize" :autoFocus true :style {:width "48%"}
                            :predicates [(input/integer "Master Disk Size")]}]]
          (style/create-form-label "Worker Machine Type")
          [input/TextField {:ref "workerMachineType" :autoFocus true :style {:width "100%"}
                            :predicates [(input/alphanumeric_- "Worker Machine Type")]}]
          (style/create-form-label "Worker Disk Size")
          [input/TextField {:ref "workerDiskSize" :autoFocus true :style {:width "100%"}
                            :predicates [(input/integer "Worker Disk Size")]}]
          (style/create-form-label "Number of Worker Local SSDs")
          [input/TextField {:ref "numberOfWorkerLocalSSDs" :autoFocus true :style {:width "100%"}
                            :predicates [(input/integer "Number of Worker Local SSDs")]}]
          (style/create-form-label "Number of Preemptible Workers")
          [input/TextField {:ref "numberOfPreemptibleWorkers" :autoFocus true :style {:width "100%"}
                            :predicates [(input/integer "Number of Preemptible Workers")]}]
          (when-not (= 0 (:label-fields @state))
            [:div {} [:div {:display "inline-block" }
                      [:span {:style {:paddingLeft "6%" :paddingRight "42.5%"}} "Key"]
                      [:span {} "Value"]]
             (map (fn [i]
                    [:div {:display "inline-block"}
                     [:a {:style {:color (:text-light style/colors) :marginRight "3%"
                                  :minHeight 30 :minWidth 30}
                          :href "javascript:;"
                          :onClick (fn [] (swap! state update-in [:label-fields] dec))}
                      (icons/render-icon {:style {:fontSize "80%"}} :close)]
                     [input/TextField {:ref (str "key" i) :autoFocus true :style {:width "45%" :marginRight "4%"}
                                       :predicates [(input/nonempty "key")
                                                    (input/alphanumeric_- "key")]}]
                     [input/TextField {:ref (str "val" i) :autoFocus true :style {:width "45%"}
                                       :predicates [(input/nonempty "val")
                                                    (input/alphanumeric_- "val")]}]])
                  (range (:label-fields @state)))])
          [buttons/Button {:text "Add Label" :icon :add-new :style {:marginBottom 10}
                         :onClick #(swap! state  update-in [:label-fields] inc)}]
          [comps/ErrorViewer {:error server-error}]
          (style/create-validation-error-message validation-errors)])}]))
  :-create-cluster
  (fn [{:keys [this state refs props]}]
    (swap! state dissoc :server-error :validation-errors)
    (let [[clusterNameCreate extensionUri numberOfWorkers masterMachineType masterDiskSize workerMachineType workerDiskSize numberOfWorkerLocalSSDs numberOfPreemptibleWorkers & fails]
            (input/get-and-validate refs "clusterNameCreate" "extensionURI" "numberOfWorkers" "masterMachineType" "masterDiskSize" "workerMachineType" "workerDiskSize" "numberOfWorkerLocalSSDs" "numberOfPreemptibleWorkers")
          payload {:bucketPath ""
                   :labels (react/call :-process-labels this)}
          machineConfig (react/call :-process-machine-config this)]
      (if fails
        (swap! state assoc :validation-errors fails)
        (do (swap! state assoc :creating? true)
            (endpoints/call-ajax-leo
               {:endpoint (endpoints/create-cluster (get-in props [:workspace-id :namespace]) (input/get-text refs "clusterNameCreate"))
                :payload (assoc (if (= "" (:jupyterExtensionUri extensionUri)) payload (merge payload extensionUri))
                                            :machineConfig machineConfig)
                :headers utils/content-type=json
                :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                           (swap! state dissoc :creating?)
                           (if success?
                             ((:dismiss props)) ;if success, update the table?
                             (swap! state assoc :server-error {:error status-text})))})))))
  :-process-labels
  (fn [{:keys [state refs]}]
    (zipmap (map #(keyword (input/get-text refs (str "key" %))) (range (:label-fields @state)))
            (map #(input/get-text refs (str "val" %)) (range (:label-fields @state)))))
  :-process-machine-config
  (fn [{:keys [state refs]}]
   (let [getInt #(if (string/blank? %) % (js/parseInt %))
         machineConfig {:numberOfWorkers (getInt (input/get-text refs "numberOfWorkers"))
                        :masterMachineType (input/get-text refs "masterMachineType")
                        :masterDiskSize (getInt (input/get-text refs "masterDiskSize"))
                        :workerMachineType (input/get-text refs "workerMachineType")
                        :workerDiskSize (getInt (input/get-text refs "workerDiskSize"))
                        :numberOfWorkerLocalSSDs (getInt (input/get-text refs "numberOfWorkerLocalSSDs"))
                        :numberOfPreemptibleWorkers (getInt (input/get-text refs "numberOfPreemptibleWorkers"))}]
     (apply dissoc
                       machineConfig
                       (for [[k v] machineConfig :when (or (nil? v) (string/blank? v))] k))
     ))})

(react/defc- ClusterDeleter
 {:render
  (fn [{:keys [state this props]}]
    (let [{:keys [deleting? server-error]} @state
          {:keys [cluster-to-delete]} props]
      [modals/OKCancelForm
       {:header "Delete Cluster"
        :dismiss (:dismiss props)
        :ok-button {:text "Delete"
                    :onClick #(this :-delete-cluster)}
        :content
        (react/create-element
         [:div {:style {:marginBottom -20}}
          (when deleting? [comps/Blocker {:banner "Deleting cluster..."}])
          [:div {} (str "Are you sure you want to delete cluster " cluster-to-delete "?")]
          [comps/ErrorViewer {:error server-error}]
          ])}]))
  :-delete-cluster
  (fn [{:keys [this state refs props]}]
    (let [{:keys [cluster-to-delete]} props]
      (swap! state assoc :deleting? true)
      (swap! state dissoc :server-error)
      (endpoints/call-ajax-leo
       {:endpoint (endpoints/delete-cluster (get-in props [:workspace-id :namespace]) cluster-to-delete)
        :headers utils/content-type=json
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state dissoc :deleting?)
                   (if success?
                     (do ((:dismiss props)) ) ;if success, update the table?
                     (swap! state assoc :server-error (get-parsed-response false))))})))})


(react/defc- NotebooksTable
 {
  :render
  (fn [{:keys [state props this]}]
    (let [{:keys [clusters toolbar-items]} props]
      [:div {}
       (when (:show-delete-dialog? @state) [ClusterDeleter (assoc props :dismiss #(swap! state dissoc :show-delete-dialog?)
                                                                        :cluster-to-delete (:cluster-to-delete @state))])
       [Table
        {:data clusters
         :body {:empty-message "There are no clusters to display."
                :style table-style/table-light
                :behavior {:reorderable-columns? false}
                :columns
                [{:header "" :initial-width 30
                  :as-text :clusterName :sort-by :clusterName
                  :render
                  (fn [cluster]
                    (if (= (:status cluster) "Running")
                      [:a {:style {:color (:text-light style/colors)
                                   :minHeight 30 :minWidth 30}
                           :href "javascript:;"
                           :onClick (fn [] (swap! state assoc :show-delete-dialog? true :cluster-to-delete (:clusterName cluster)))
                           :id (:id props) :data-test-id "x-button"}
                       (icons/render-icon {:style {:fontSize "80%"}} :close)]))}

                 {:header "Name" :initial-width 200
                  :as-text :clusterName :sort-by :clusterName
                  :render
                  (fn [cluster]
                    (if (= (:status cluster) "Running")
                      [:a {:style {:textDecoration "none"}
                           :href (str "https://leonardo.dsde-dev.broadinstitute.org/notebooks/" (:googleProject cluster) "/" (:clusterName cluster))
                           :onClick #(utils/set-access-token-cookie (utils/get-access-token))
                           :target "_blank"}
                       (str (:clusterName cluster))]
                      (:clusterName cluster)))}
                 {:header "Status" :initial-width 100
                  :column-data :status
                  :render (fn [status]
                            [:div {:style {:height table-style/table-icon-size}}
                             (icon-for-cluster-status status) status])}
                 {:header "Created Date" :initial-width 200
                  :column-data :createdDate}
                 ;(table-utils/date-column {:column-data (comp :createdDate :workspace)})
                 {:header "Master Machine Type" :initial-width 100
                  :column-data (comp :masterMachineType :machineConfig)}
                 {:header "Master Disk Size" :initial-width 100
                  :column-data (comp :masterDiskSize :machineConfig)}
                 {:header "Workers" :initial-width 100
                  :column-data (comp :numberOfWorkers :machineConfig)}
                 {:header "Worker Machine Type" :initial-width 100
                  :column-data (comp :workerMachineType :machineConfig)}
                 {:header "Worker Local SSDs" :initial-width 100
                  :column-data (comp :numberOfWorkerLocalSSDs :machineConfig)}
                 {:header "Preemptible Workers" :initial-width 100
                  :column-data (comp :numberOfPreemptibleWorkers :machineConfig)}
                 {:header "Labels" :initial-width "100%"
                  :column-data :labels
                  :render
                  (fn [labels]
                    (map
                     (fn [label] (str (name (key label)) "=" (val label) "\n\n"))  ;figure out how to make it stack
                     labels))}]}
         :toolbar {:get-items (constantly toolbar-items)}}]]))})

;; Auto reload: Call :get-clusters-list every 3 seconds in Page. If new clusters is different from old, change state, triggering re-render of table

(react/defc NotebooksContainer
  {:refresh
   (fn [])
   :render
   (fn [{:keys [state props this]}]
     (let [{:keys [server-response]} @state
           {:keys [clusters server-error]} server-response]
       [:div {}
        [:div {:display "inline-flex"}
         (when (:show-create-dialog? @state) [ClusterCreator (assoc props :dismiss #(swap! state dissoc :show-create-dialog?))])
         (create-section-header "Notebooks")
         [:div {:style {:paddingBottom 10}}]
         (if server-error [comps/ErrorViewer {:error server-error}]
                          (if clusters [NotebooksTable
                                        (assoc props :toolbar-items [flex/spring
                                                                     [buttons/Button {:text "Create Cluster..."  :onClick #(swap! state assoc :show-create-dialog? true) :style {:marginRight 7}}]]
                                                     :clusters clusters)]
                                       [:div {} "NOPE"]))]]))
   :get-clusters-list
   (fn [{:keys [state this]}]
     (utils/log "get clusters list")
     (endpoints/call-ajax-leo
      {:endpoint (endpoints/get-clusters-list {})  ;;For now, just get all labels
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (if-not (= (:clusters @state) (get-parsed-response)) (swap! state assoc :server-response {:clusters (filter #(not (= "Deleted" (:status %))) (get-parsed-response))}))    ;if success, update the table?
                    (do (utils/log "failure") (swap! state assoc :server-response {:server-error (get-parsed-response false)})))
                  (js/setTimeout (fn [] (react/call :get-clusters-list this)) 6000))}))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :get-clusters-list this))})



