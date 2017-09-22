(ns broadfcui.page.workspace.analysis.tab
  (:require
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
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.analysis.igv :refer [IGVContainer]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.page.workspace.analysis.track-selector :refer [TrackSelectionDialog]]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.utils :as utils]
   ))


(def ^:private tracks-cache (atom {}))

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
    (let [{:keys [creating? server-error]} @state]
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
                            :predicates [(input/nonempty "Cluster name")
                                         (input/alphanumeric_- "Cluster name")]}]
          (style/create-form-label "Service Account")
          [input/TextField {:ref "serviceAccountCreate" :autoFocus true :style {:width "100%"}
                            :predicates [(input/nonempty "Service Account")
                                         (input/alphanumeric_- "Service Account")]}]
          (style/create-form-label "Extension URI")
          [input/TextField {:ref "extensionUriCreate" :autoFocus true :style {:width "100%"}}]
          (when-not (= 0 (:label-fields @state))
             [:div {} [:div {:display "inline-block" } [:span {:style {:paddingRight 180}} "Key"] [:span {} "Value"]]
             (map (fn [i]
                    [:div {:display "inline-block"}
                     [input/TextField {:ref (str "key" i) :autoFocus true :style {:width "50%"}
                                  :predicates [(input/nonempty "key")
                                               (input/alphanumeric_- "key")]}]
                     [input/TextField {:ref (str "val" i) :autoFocus true :style {:width "50%"}
                                  :predicates [(input/nonempty "val")
                                               (input/alphanumeric_- "val")]}]])
              (range (:label-fields @state)))])
          [comps/Button {:text "Add Label" :icon :add-new
                         :onClick #(swap! state  update-in [:label-fields] inc)}]
          [comps/ErrorViewer {:error server-error}]])}]))
  :-create-cluster
  (fn [{:keys [this state refs props]}]
    (let [payload {:bucketPath ""
                   :serviceAccount (input/get-text refs "serviceAccountCreate")
                   :labels (react/call :-process-labels this)}
          extensionUri {:jupyterExtensionUri (input/get-text refs "extensionUriCreate")}]
     (swap! state assoc :creating? true)
     (swap! state dissoc :server-error)
     (endpoints/call-ajax-leo
      {:endpoint (endpoints/create-cluster (get-in props [:workspace-id :namespace]) (input/get-text refs "clusterNameCreate"))
       :payload (utils/log (if (= "" (:jupyterExtensionUri extensionUri)) payload (merge payload extensionUri)))
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                 (swap! state dissoc :creating?)
                 (if success?
                   ((:dismiss props)) ;if success, update the table?
                   (swap! state assoc :server-error {:error status-text})))})))
  :-process-labels
  (fn [{:keys [state refs]}]
     (zipmap (map #(keyword (input/get-text refs (str "key" %))) (range (:label-fields @state)))
             (map #(input/get-text refs (str "val" %)) (range (:label-fields @state)))))})

(react/defc- ClusterDeleter
 {:render
  (fn [{:keys [state this props]}]
    (let [{:keys [deleting? server-error]} @state
          {:keys [cluster-to-delete]} props]
      (utils/log @state)
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
                      (icons/icon {:style {:fontSize "80%"}} :close)]))}

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
                 {:header "Status" :initial-width 150
                  :column-data :status
                  :render (fn [status]
                            [:div {:style {:height table-style/table-icon-size}}
                             (icon-for-cluster-status status) status])}
                 {:header "Created Date" :initial-width 200
                  :column-data :createdDate}
                 ;(table-utils/date-column {:column-data (comp :createdDate :workspace)})
                 {:header "Labels" :initial-width "100%"
                  :column-data :labels
                  :render
                  (fn [labels]
                    (map
                     (fn [label] (str (name (key label)) "=" (val label) "\n\n"))  ;figure out how to make it stack
                     labels))}]}
         :toolbar {:get-items (constantly toolbar-items)}}]]))})

;; Auto reload: Call :get-clusters-list every 3 seconds in Page. If new clusters is different from old, change state, triggering re-render of table

(react/defc Page
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
                                                                     [comps/Button {:text "Create Cluster..."  :onClick #(swap! state assoc :show-create-dialog? true) :style {:marginRight 7}}]]
                                                     :clusters clusters)]
                                       [:div {} "NOPE"]))]]))
   :get-clusters-list
   (fn [{:keys [state this]}]
     (utils/log "about to cal get-clusters-list")
     (endpoints/call-ajax-leo
        {:endpoint (endpoints/get-clusters-list {})  ;;For now, just get all labels
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :creating?)
                    (if success?
                      (if-not (= (:clusters @state) (get-parsed-response)) (swap! state assoc :server-response {:clusters (filter #(not (= "Deleted" (:status %))) (get-parsed-response))}))    ;if success, update the table?
                      (do (utils/log "failure") (swap! state assoc :server-response {:server-error (get-parsed-response false)})))
                    (js/setTimeout (fn [] (react/call :get-clusters-list this)) 6000))}))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :get-clusters-list this))})