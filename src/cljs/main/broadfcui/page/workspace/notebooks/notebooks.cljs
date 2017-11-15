(ns broadfcui.page.workspace.notebooks.notebooks
  (:require
   [dmohs.react :as react]
   [clojure.set]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.input :as input]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.components.modals :as modals]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.utils :as utils]
   ))


(def machineTypes ["n1-standard-1"
                   "n1-standard-2"
                   "n1-standard-4"
                   "n1-standard-8"
                   "n1-standard-16"
                   "n1-standard-32"
                   "n1-standard-64"
                   "n1-standard-96"
                   "n1-highcpu-4"
                   "n1-highcpu-8"
                   "n1-highcpu-16"
                   "n1-highcpu-32"
                   "n1-highcpu-64"
                   "n1-highcpu-96"
                   "n1-highmem-2"
                   "n1-highmem-4"
                   "n1-highmem-8"
                   "n1-highmem-16"
                   "n1-highmem-32"
                   "n1-highmem-64"
                   "n1-highmem-96"])


(def spinner-icon
  [:span {:style {:display "inline-flex" :alignItems "center" :justifyContent "center" :verticalAlign "middle"
                  :width table-style/table-icon-size :height table-style/table-icon-size
                  :borderRadius 3 :margin "-4px 4px 0 0"}
          :data-test-id "status-icon" :data-test-value "unknown"}
   [comps/Spinner {:size 12}]])

(defn icon-for-cluster-status [status]
  (case status
    ("Deleted" "Error") (moncommon/render-failure-icon)
    ("Creating" "Updating" "Deleting") spinner-icon
    "Running" (moncommon/render-success-icon)
    "Unknown" (moncommon/render-unknown-icon)))

(defn create-inline-form-label [text]
  [:span {:style {:marginBottom "0.16667em" :fontSize "88%"}} text])


(react/defc- ClusterCreator
  {:refresh
   (fn [])
   :get-initial-state
   (fn []
     {:labels []
      :label-gensym (gensym)})
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
           [Collapse
            {:style {:marginLeft -20} :default-hidden? true
             :title [:span {:style {:marginBottom 20 :fontStyle "italic"}} "Optional Settings..."]
             :contents
             (react/create-element
              [:div {}
               (style/create-form-label "Extension URI")
               [input/TextField {:ref "extensionURI" :autoFocus true :style {:width "100%"}}]
               [:div {:display "inline-block"}
                [:span {:style {:paddingRight "19%"}} (create-inline-form-label "Master Machine Type")]
                [:span {} (create-inline-form-label "Master Disk Size")]]
               [:div {:display "inline-block"}
                (style/create-identity-select {:ref "masterMachineType" :style {:width "48%" :marginRight "4%"}
                                               :defaultValue "n1-standard-4"} machineTypes)
                [input/TextField {:ref "masterDiskSize" :autoFocus true :style {:width "41%"}
                                  :defaultValue 500 :min 0 :type "number"}]
                [:span {:style {:marginLeft "2%"}} (create-inline-form-label "GB")]]
               (style/create-form-label "Workers")
               [input/TextField {:ref "numberOfWorkers" :autoFocus true :style {:width "100%"}
                                 :defaultValue 0 :min 0 :type "number"}]
               [:div {:display "inline-block"}
                [:span {:style {:paddingRight "19%"}} (create-inline-form-label "Worker Machine Type")]
                [:span {} (create-inline-form-label "Worker Disk Size")]]
               [:div {:display "inline-block"}
                (style/create-identity-select {:ref "workerMachineType" :style {:width "48%" :marginRight "4%"}
                                               :defaultValue "n1-standard-4"} machineTypes)
                [input/TextField {:ref "workerDiskSize" :autoFocus true :style {:width "41%"}
                                  :defaultValue 500 :min 0 :type "number"}]
                [:span {:style {:marginLeft "2%"}} (create-inline-form-label "GB")]]
               [:div {:display "inline-block"}
                [:span {:style {:paddingRight "23%"}} (create-inline-form-label "Worker Local SSDs")]
                [:span {} (create-inline-form-label "Preemptible Workers")]]
               [:div {:display "inline-block"}
                [input/TextField {:ref "numberOfWorkerLocalSSDs" :autoFocus true :style {:width "48%" :marginRight "4%"}
                                  :defaultValue 0 :min 0 :type "number"}]
                [input/TextField {:ref "numberOfPreemptibleWorkers" :autoFocus true :style {:width "48%"}
                                  :defaultValue 0 :min 0 :type "number"}]]
               (when (seq (:labels @state))
                 [:div {:key (:label-gensym @state)}
                  [:div {:display "inline-block"}
                   [:span {:style {:paddingRight "47%"}} (create-inline-form-label "Key")]
                   [:span {} (create-inline-form-label "Value")]]
                  (map-indexed (fn [i label]
                                 [:div {:display "inline-block" :style {:marginBottom 10}}
                                  (links/create-internal
                                    {:style {:color (:text-light style/colors)
                                             :marginRight "2.5%" :marginLeft -20 :minHeight 30 :minWidth 30}
                                     :href "javascript:;"
                                     :onClick (fn [] (swap! state #(-> % (assoc :label-gensym (gensym))
                                                                       (update :labels utils/delete i))))}
                                    (icons/render-icon {} :remove))
                                  [input/TextField {:style {:ref (str "key" i)
                                                            :marginBottom 0 :width "47.5%" :marginRight "4%"}
                                                    :defaultValue (first label)
                                                    :onChange #(swap! state update-in [:labels i]
                                                                      assoc 0 (-> % .-target .-value))}]
                                  [input/TextField {:style {:ref (str "val" i)
                                                            :marginBottom 0 :width "47.5%"}
                                                    :defaultValue (last label)
                                                    :onChange #(swap! state update-in [:labels i]
                                                                      assoc 1 (-> % .-target .-value))}]
                                  (common/clear-both)])
                               (:labels @state))])
               [buttons/Button {:text "Add Label" :icon :add-new :style {:marginBottom 10}
                                :onClick (fn []
                                           (swap! state #(-> %
                                                             (update :labels conj ["" ""])
                                                             (assoc :label-gensym (gensym)))))}]])}]
           [comps/ErrorViewer {:error server-error}]
           (style/create-validation-error-message validation-errors)])}]))
   :-create-cluster
   (fn [{:keys [this state refs props]}]
     (swap! state dissoc :server-error :validation-errors)
     (let [[clusterNameCreate extensionURI & fails] (input/get-and-validate refs "clusterNameCreate" "extensionURI")
           payload {:bucketPath ""
                    :labels (this :-process-labels)}
           machineConfig (this :-process-machine-config)]
       (if fails
         (swap! state assoc :validation-errors fails)
         (do (swap! state assoc :creating? true)
             (endpoints/call-ajax-leo
              {:endpoint (endpoints/create-cluster (get-in props [:workspace-id :namespace]) clusterNameCreate)
               :payload (assoc (if (= "" (:jupyterExtensionUri extensionURI)) payload (merge payload extensionURI))
                          :machineConfig machineConfig)
               :headers utils/content-type=json
               :on-done (fn [{:keys [success? get-parsed-response]}]
                          (swap! state dissoc :creating?)
                          (if success?
                            (do ((:dismiss props)) ((:reload-after-create props))) ;if success, update the table?
                            (swap! state assoc :server-error (get-parsed-response false))))})))))
   :-process-labels
   (fn [{:keys [state]}]
     (zipmap (map (comp keyword first) (:labels @state))
             (map last (:labels @state))))
   :-process-machine-config
   (fn [{:keys [refs]}]
     (let [getInt #(if (string/blank? %) % (js/parseInt %))
           machineConfig {:numberOfWorkers (getInt (input/get-text refs "numberOfWorkers"))
                          :masterMachineType (.-value (@refs "masterMachineType"))
                          :masterDiskSize (getInt (input/get-text refs "masterDiskSize"))
                          :workerMachineType (.-value (@refs "workerMachineType"))
                          :workerDiskSize (getInt (input/get-text refs "workerDiskSize"))
                          :numberOfWorkerLocalSSDs (getInt (input/get-text refs "numberOfWorkerLocalSSDs"))
                          :numberOfPreemptibleWorkers (getInt (input/get-text refs "numberOfPreemptibleWorkers"))}]
       (apply dissoc
              machineConfig
              (for [[k v] machineConfig :when (or (nil? v) (string/blank? v))] k))))})

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
          [:div {}
           (when deleting? [comps/Blocker {:banner "Deleting cluster..."}])
           [:div {} (str "Are you sure you want to delete cluster " cluster-to-delete "?")]
           [comps/ErrorViewer {:error server-error}]])}]))
   :-delete-cluster
   (fn [{:keys [state props]}]
     (let [{:keys [cluster-to-delete]} props]
       (swap! state assoc :deleting? true)
       (swap! state dissoc :server-error)
       (endpoints/call-ajax-leo
        {:endpoint (endpoints/delete-cluster (get-in props [:workspace-id :namespace]) cluster-to-delete)
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :deleting?)
                    (if success?
                      (do ((:dismiss props)) ((:reload-after-delete props))) ;if success, update the table?
                      (swap! state assoc :server-error (get-parsed-response false))))})))})


(react/defc- NotebooksTable
  {:render
   (fn [{:keys [state props]}]
     (let [{:keys [clusters toolbar-items]} props]
       [:div {}
        (when (:show-delete-dialog? @state)
          [ClusterDeleter (assoc props :dismiss #(swap! state dissoc :show-delete-dialog?)
                                       :cluster-to-delete (:cluster-to-delete @state))])
        [Table
         {:data clusters :data-test-id "spark-clusters-table"
          :body {:empty-message "There are no clusters to display."
                 :style table-style/table-light
                 :fixed-column-count 1
                 :columns
                 [{:id "delete" :initial-width 30
                   :resizable? false :sortable? false :filterable? false :hidden? true
                   :as-text :clusterName :sort-by :clusterName
                   :render
                   (fn [cluster]
                     (if (= (:status cluster) "Running")
                       (links/create-internal
                         {:data-test-id "x-button"
                          :id (:id props)
                          :style {:color (:text-light style/colors)
                                  :minHeight 30 :minWidth 30}
                          :onClick #(swap! state assoc :show-delete-dialog? true :cluster-to-delete (:clusterName cluster))}
                         (icons/render-icon {} :delete))))}

                  {:header "Name" :initial-width 150
                   :as-text :clusterName :sort-by :clusterName :sort-initial :asc
                   :render
                   (fn [cluster]
                     (if (= (:status cluster) "Running")
                       (links/create-external
                        {:href (str (config/leonardo-url-root) "/notebooks/" (:googleProject cluster) "/" (:clusterName cluster))
                         :onClick #(utils/set-notebooks-access-token-cookie (utils/get-access-token))}
                        (:clusterName cluster))
                       (:clusterName cluster)))}
                  {:header "Status" :initial-width 100
                   :column-data :status
                   :render (fn [status]
                             [:div {:style {:height table-style/table-icon-size}}
                              (icon-for-cluster-status status) status])}
                  (table-utils/date-column {:column-data :createdDate :style {}})
                  {:header "Master Machine Type" :initial-width 150
                   :column-data (comp :masterMachineType :machineConfig)}
                  {:header "Master Disk Size (GB)" :initial-width 150
                   :column-data (comp :masterDiskSize :machineConfig)}
                  {:header "Workers" :initial-width 80
                   :column-data (comp :numberOfWorkers :machineConfig)}
                  {:header "Worker Machine Type" :initial-width 150
                   :column-data (comp :workerMachineType :machineConfig)}
                  {:header "Worker Disk Size (GB)" :initial-width 150
                   :column-data (comp :workerMachineType :machineConfig)}
                  {:header "Worker Local SSDs" :initial-width 130
                   :column-data (comp :numberOfWorkerLocalSSDs :machineConfig)}
                  {:header "Preemptible Workers" :initial-width 150
                   :column-data (comp :numberOfPreemptibleWorkers :machineConfig)}
                  {:header "Labels" :initial-width :auto
                   :column-data #(dissoc (:labels %) :serviceAccount :clusterName :googleProject :googleBucket)
                   :sort-by (comp vec keys)
                   :render
                   (fn [labels]
                     [:div {}
                      (map (fn [label]
                             [:span {:style {:backgroundColor (:background-light style/colors)
                                             :borderTop style/standard-line :borderBottom style/standard-line
                                             :borderRadius 12 :marginRight 4 :padding "0.25rem 0.75rem"}}
                              (if (string/blank? (val label))
                                (name (key label))
                                (str (name (key label)) " | " (val label) "\n\n"))])
                           (into (sorted-map) labels))])}]}
          :toolbar {:get-items (constantly toolbar-items)}}]]))})

(react/defc NotebooksContainer
  {:refresh
   (fn [{:keys [this]}]
     (this :-get-clusters-list))
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [server-response]} @state
           {:keys [clusters server-error]} server-response]
        [:div {:display "inline-flex"}
         (when (:show-create-dialog? @state)
           [ClusterCreator (assoc props :dismiss #(swap! state dissoc :show-create-dialog?)
                                        :reload-after-create #(this :-get-clusters-list))])
         [:div {} [:span {:data-test-id "spark-clusters-title" :style {:fontSize "125%" :fontWeight 500 :paddingBottom 10}} "Spark Clusters"]]
         (if server-error
           [comps/ErrorViewer {:error server-error}]
           (if clusters
             [NotebooksTable
              (assoc props :toolbar-items [flex/spring [buttons/Button {:text "Create Cluster..." :style {:marginRight 7}
                                                                        :data-test-id "create-modal-button"
                                                                        :onClick #(swap! state assoc :show-create-dialog? true)}]]
                           :clusters clusters
                           :reload-after-delete #(this :-get-clusters-list))]))]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :-get-clusters-list))
   :-get-clusters-list
   (fn [{:keys [props state this]}]
     (endpoints/call-ajax-leo
      {:endpoint (endpoints/get-clusters-list)
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (when-not (= (:clusters @state) (get-parsed-response))
                      (swap! state assoc :server-response {:clusters (filter #(= (get-in props [:workspace-id :namespace]) (:googleProject %)) (get-parsed-response))}))
                    (swap! state assoc :server-response {:server-error (get-parsed-response false)}))
                  (let [statuses (set (map #(:status %) (get-parsed-response)))]
                    (when (or (contains? statuses "Creating") (contains? statuses "Updating") (contains? statuses "Deleting"))
                      (js/setTimeout (fn [] (this :-get-clusters-list)) 10000))))}))})
