(ns broadfcui.page.workspace.notebooks.notebooks
  (:require
   [dmohs.react :as react]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.config :as config]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.notebooks.create_notebook :refer [NotebookCreator]]
   [broadfcui.page.workspace.notebooks.create_cluster :refer [ClusterCreator]]
   [broadfcui.page.workspace.notebooks.delete_cluster :refer [ClusterDeleter]]
   [broadfcui.page.workspace.notebooks.cluster_details :refer [ClusterDetailsViewer]]
   [broadfcui.page.workspace.notebooks.choose_cluster :refer [ChooseClusterViewer]]
   [broadfcui.page.workspace.notebooks.cluster_error :refer [ClusterErrorViewer]]
   [broadfcui.page.workspace.notebooks.rename_notebook :refer [NotebookRenamer]]
   [broadfcui.page.workspace.notebooks.delete_notebook :refer [NotebookDeleter]]
   [broadfcui.page.workspace.notebooks.duplicate_notebook :refer [NotebookDuplicator]]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.page.workspace.notebooks.utils :as notebook-utils]
   ))

(def spinner-icon
  [:span {:style {:display "inline-flex" :alignItems "center" :justifyContent "center" :verticalAlign "middle"
                  :width table-style/table-icon-size :height table-style/table-icon-size
                  :borderRadius 3 :margin "-4px 4px 0 0"}
          :data-test-id "status-icon" :data-test-value "unknown"}
   (spinner)])

(def pause-icon
  [:span {:style {:display "inline-flex" :alignItems "center" :justifyContent "center" :verticalAlign "middle"
                  :width table-style/table-icon-size :height table-style/table-icon-size
                  :borderRadius 3 :margin "-4px 4px 0 0"}
          :data-test-id "status-icon" :data-test-value "unknown"}
   (icons/render-icon {} :pause)])

(def play-icon
  [:span {:style {:display "inline-flex" :alignItems "center" :justifyContent "center" :verticalAlign "middle"
                  :width table-style/table-icon-size :height table-style/table-icon-size
                  :borderRadius 3 :margin "-4px 4px 0 0"}
          :data-test-id "status-icon" :data-test-value "unknown"}
   (icons/render-icon {} :play)])

(react/defc NotebooksTable
  {:render
   (fn [{:keys [state props this]}]
     (let [{:keys [choose-notebook]} @state
           {:keys [notebooks cluster-map pet-token toolbar-items]} props]
       [:div {}
        (when (:show-cluster-choose-dialog? @state)
          [ChooseClusterViewer (assoc props :dismiss #(swap! state dissoc :show-cluster-choose-dialog?)
                                            :choose-notebook choose-notebook
                                            :reload-after-choose #(this :-choose-cluster %)
                                            :show-create-cluster #(swap! state assoc :show-cluster-create-dialog? true))])
        (when (:show-cluster-create-dialog? @state)
          [ClusterCreator (assoc props :dismiss #(swap! state dissoc :show-cluster-create-dialog?)
                                       :choose-notebook choose-notebook
                                       :reload-after-choose #(this :-choose-cluster %))])
        (when (:show-cluster-delete-dialog? @state)
          [ClusterDeleter (assoc props :dismiss #(swap! state dissoc :show-cluster-delete-dialog?)
                                       :cluster-to-delete (:cluster-to-delete @state))])
        (when (:show-cluster-details-dialog? @state)
          [ClusterDetailsViewer (assoc props :dismiss #(swap! state dissoc :show-cluster-details-dialog?)
                                       :cluster-to-display-details (:cluster-to-display-details @state))])
        (when (:show-error-dialog? @state)
          [ClusterErrorViewer (assoc props :dismiss #(swap! state dissoc :show-error-dialog? :errored-cluster-to-show)
                                           :cluster-to-view (:errored-cluster-to-show @state))])
        (when (:show-notebook-rename-dialog? @state)
          [NotebookRenamer (assoc props :dismiss #(swap! state dissoc :show-notebook-rename-dialog?)
                                        :choose-notebook choose-notebook)])
        (when (:show-notebook-duplicate-dialog? @state)
          [NotebookDuplicator (assoc props :dismiss #(swap! state dissoc :show-notebook-duplicate-dialog?)
                                           :choose-notebook choose-notebook)])
        (when (:show-notebook-delete-dialog? @state)
          [NotebookDeleter (assoc props :dismiss #(swap! state dissoc :show-notebook-delete-dialog?)
                                        :choose-notebook choose-notebook)])
        [Table
         {:data-test-id "notebooks-table" :data notebooks
          :body
          {:empty-message "There are no notebooks to display."
           :style table-style/table-light
           :fixed-column-count 1
           :column-defaults {"shown" ["notebook-menu" "Name" "Last Modified" "cluster-menu" "Cluster" "Status"]}
           :columns
           [{:id "notebook-menu" :initial-width 30
             :resizable? false :sortable? false :filterable? false :hidden? true
             :as-text :name :sort-by :name
             :render
             (fn [notebook]
               (dropdown/render-dropdown-menu
                {:label
                 (icons/render-icon {} :ellipsis-v)
                 :width 100
                 :button-style {:height :auto :marginRight "0.5rem" :marginBottom "0.4rem"}
                 :items
                 [{:text [:span {:style {:display "inline-flex"}} "Rename"]
                   :dismiss #(swap! state assoc :show-notebook-rename-dialog? true :choose-notebook notebook)}
                  {:text [:span {:style {:display "inline-flex"}} "Duplicate"]
                   :dismiss #(swap! state assoc :show-notebook-duplicate-dialog? true :choose-notebook notebook)}
                  {:text [:span {:style {:display "inline-flex"}} "Delete"]
                   :dismiss #(swap! state assoc :show-notebook-delete-dialog? true :choose-notebook notebook)}]}))}
            {:header "Name" :initial-width 250
             :as-text :name :sort-by :name :sort-initial :asc
             :render #(notebook-utils/notebook-name %)}
            (table-utils/date-column {:header "Last Modified" :column-data :updated :style {}})
            {:id "cluster-menu" :initial-width 30
             :resizable? false :sortable? false :filterable? false :hidden? true
             :as-text :name :sort-by :name
             :render
             (fn [notebook]
               (when (contains? notebook :cluster-name)
                 (dropdown/render-dropdown-menu
                  {:label
                   (icons/render-icon {} :settings)
                   :width 100
                   :button-style {:height :auto :marginRight "0.5rem" :marginBottom "0.4rem"}
                   :items
                   (filter (comp not nil?)
                           ; Details - always displayed when there is an associated cluster
                           [{:text [:span {:style {:display "inline-flex"}} "Details"]
                             :dismiss #(swap! state assoc :show-cluster-details-dialog? true :cluster-to-display-details (:cluster-name notebook))}
                            ; Choose - always displayed when there is an associated cluster
                            {:text [:span {:style {:display "inline-flex"}} "Choose..."]
                             :dismiss #(swap! state assoc :show-cluster-choose-dialog? true :choose-notebook notebook)}
                            ; Delete - displayed when the associated cluster is a cluster associated in Running or Stopped status
                            (when (notebook-utils/contains-statuses [(get cluster-map (:cluster-name notebook))] ["Running" "Error" "Stopped"])
                              {:text [:span {:style {:display "inline-flex"}} "Delete"]
                               :dismiss #(swap! state assoc :show-cluster-delete-dialog? true :cluster-to-delete (:cluster-name notebook))})])})))}
            {:header "Cluster" :initial-width 150
             :as-text :name :sort-by :name :sort-initial :asc
             :render
             (fn [notebook]
               (if-let [cluster (get cluster-map (:cluster-name notebook))]
                 (if (= (:status cluster) "Running")
                   (let [workspace-name (get-in props [:workspace :workspace :name])]
                     (links/create-external {:data-test-id (str (:clusterName cluster) "-link")
                                             :href (notebook-utils/leo-notebook-url cluster workspace-name notebook)} (:clusterName cluster)))
                   (:clusterName cluster))
                 (links/create-internal
                  {:data-test-id (str (:name notebook) "-chooser")
                   :style {:textDecoration "none" :color (:button-primary style/colors)}
                   :onClick #(swap! state assoc :show-cluster-choose-dialog? true :choose-notebook notebook)}
                  "Choose...")))}
            {:header "Status" :initial-width 150
             :as-text :name :sort-by :name
             :render
             (fn [notebook]
               (if-let [cluster (get cluster-map (:cluster-name notebook))]
                 (this :-render-cluster-status cluster)))}]}
          :toolbar {:get-items (constantly toolbar-items)}}]]))

   :-render-cluster-status
   (fn [{:keys [state props]} cluster]
     (let [clusterNameStatusId (str (:clusterName cluster) "-status")
           cluster-map (:cluster-map props)
           stop-cluster-link (links/create-internal {:data-test-id clusterNameStatusId
                                                     :style {:textDecoration "none" :color (:button-primary style/colors)}
                                                     :onClick #((:stop-cluster props) cluster)}
                                                    pause-icon)
           start-cluster-link (links/create-internal {:data-test-id clusterNameStatusId
                                                      :style {:textDecoration "none" :color (:button-primary style/colors)}
                                                      :onClick #((:start-cluster props) cluster)}
                                                     play-icon)
           view-error-link (links/create-internal {:data-test-id clusterNameStatusId
                                                   :style {:textDecoration "none" :color (:button-primary style/colors)}
                                                   :onClick #(swap! state assoc :show-error-dialog? true :errored-cluster-to-show cluster)}
                                                  "View error")]
       [:div {:key (when cluster-map (str (gensym))) ;this makes the spinners sync
              :style {:height table-style/table-icon-size}}
        (case (:status cluster)
          "Error" [:div {:style {:display "inline-flex"}} (moncommon/render-failure-icon) view-error-link]
          ("Creating" "Updating" "Deleting" "Starting" "Stopping") spinner-icon
          "Running" stop-cluster-link
          "Stopped" start-cluster-link
          (moncommon/render-unknown-icon))
        (when-not (= (:status cluster) "Error")
          [:span {:data-test-id clusterNameStatusId} (:status cluster)])]))

   :-choose-cluster
   (fn [{:keys [state props]} selected-cluster]
     (let [{:keys [choose-notebook]} @state]
       ((:choose-cluster props) choose-notebook selected-cluster)))})

(react/defc NotebooksContainer
  {:refresh
   (fn [{:keys [this]}]
     (this :-get-pet-token)
     (this :-get-clusters-list)
     (this :-schedule-cookie-refresh))

   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [server-response cluster-map show-create-dialog?]} @state
           {:keys [notebooks server-error]} server-response]
       [:div {:display "inline-flex"}
        (when show-create-dialog?
          [NotebookCreator (assoc props :dismiss #(swap! state dissoc :show-create-dialog?)
                                        :pet-token (:pet-token @state)
                                        :notebooks notebooks
                                        :refresh-notebooks #(this :-refresh-notebooks))])
        [:div {} [:span {:data-test-id "notebooks-title" :style {:fontSize "125%" :fontWeight 500 :paddingBottom 10 :marginLeft 10}} "Notebooks"]]
        [:div {:style {:margin 10 :fontSize "88%"}} "These are your actual notebooks in the workspace"]
        [comps/ErrorViewer {:data-test-id "notebooks-error" :error server-error}]
        (if notebooks
          [NotebooksTable
           (assoc props
             :toolbar-items [flex/spring [buttons/Button {:data-test-id "create-modal-button"
                                                          :text "Create Notebook..." :style {:marginRight 7}
                                                          :onClick #(swap! state assoc :show-create-dialog? true)}]]
             :choose-cluster #(this :-assoc-cluster-with-notebook %1 %2)
             :stop-cluster #(this :-stop-cluster %)
             :start-cluster #(this :-start-cluster %)
             :reload-after-delete #(this :-get-clusters-list %)
             :cluster-map cluster-map
             :notebooks notebooks
             :pet-token (:pet-token @state)
             :refresh-notebooks #(this :-refresh-notebooks))]
          [:div {:style {:textAlign "center"}} (spinner "Loading notebooks...")])]))

   :component-did-mount
   (fn [{:keys [this]}]
     (this :-get-pet-token)
     (this :-get-clusters-list)
     (this :-schedule-cookie-refresh)
     (.addEventListener js/window "message" (react/method this :-notebook-extension-listener)))

   :component-will-unmount
   (fn [{:keys [this locals]}]
     (swap! locals assoc :dead? true)
     (.removeEventListener js/window "message" (react/method this :-notebook-extension-listener)))

   ; Communicates with the Leo notebook extension.
   ; Use with `react/method` to return a stable binding to the function.
   :-notebook-extension-listener
   (fn [_ e]
     (when (and (= (config/leonardo-url-root) (.-origin e))
                (= "bootstrap-auth.request" (.. e -data -type)))
       (.postMessage (.-source e)
                     (clj->js {:type "bootstrap-auth.response" :body {:googleClientId (config/google-client-id)}})
                     (config/leonardo-url-root))))

   :-schedule-cookie-refresh
   (fn [{:keys [state locals this]}]
     (let [{{:keys [cluster-map]} :server-response} @state]
       (when-not (:dead? @locals)
         (when (notebook-utils/contains-statuses (vals cluster-map) ["Running"])
           (this :-process-running-clusters))
         (js/setTimeout #(this :-schedule-cookie-refresh) 120000))))

   :-process-running-clusters
   (fn [{:keys [props state this]}]
     (let [cluster-map (:cluster-map @state)
           running-clusters (filter (comp (partial = "Running") :status) (vals cluster-map))]
       (doseq [cluster running-clusters]
         (this :-localize-notebooks cluster)
         (ajax/call
          {:url (str (notebook-utils/leo-notebook-url-base cluster) "/setCookie")
           :headers (user/get-bearer-token-header)
           :with-credentials? true
           :cross-domain true
           :on-done (fn [{:keys [success? raw-response]}]
                      (when-not success?
                        (swap! state assoc :server-response {:server-error raw-response})))}))))

   :-localize-notebooks
   (fn [{:keys [props state this]} cluster]
     (let [workspaceName (:workspaceName props)
           {:keys [server-response]} @state
           {:keys [notebooks]} server-response
           filtered-notebooks (filter (comp (partial = (:clusterName cluster)) :cluster-name) notebooks)]
       (endpoints/localize-notebook (:googleProject cluster) (:clusterName cluster)
                                    ;(this :-delocalize-json)
                                    (reduce merge (conj (map (partial this :-localize-entry) filtered-notebooks) (this :-delocalize-json)))
                                    (fn [{:keys [success? get-parsed-response]}]
                                      (when-not success?
                                        (js/setTimeout #(this :-localize-notebooks cluster) 10000))))))

   :-delocalize-json
   (fn [{:keys [props]}]
     (let [workspace-name (get-in props [:workspace :workspace :name])
           bucket-name (get-in props [:workspace :workspace :bucketName])]
       {(str "~/" workspace-name "/.delocalize.json")
        (str "data:application/json," (utils/->json-string {:destination (str "gs://" bucket-name "/notebooks") :pattern ""}))}))

   :-localize-entry
   (fn [{:keys [props]} notebook]
     (let [workspace-name (get-in props [:workspace :workspace :name])
           bucket-name (get-in props [:workspace :workspace :bucketName])
           notebook-name (last (clojure.string/split (:name notebook) #"/"))]
       {(str "~/" workspace-name "/" notebook-name) (str "gs://" bucket-name "/notebooks/" notebook-name)}))

   :-stop-cluster
   (fn [{:keys [props state this]} cluster]
     (swap! state assoc :cluster-map (assoc (:cluster-map @state) (:clusterName cluster) (assoc cluster :status "Stopping")))
     (endpoints/call-ajax-leo
      {:endpoint (endpoints/stop-cluster (:googleProject cluster) (:clusterName cluster))
       :headers ajax/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (this :-get-clusters-list)
                    (swap! state assoc :server-response {:server-error (get-parsed-response false)})))}))

   :-start-cluster
   (fn [{:keys [props state this]} cluster]
     (swap! state assoc :cluster-map (assoc (:cluster-map @state) (:clusterName cluster) (assoc cluster :status "Starting")))
     (endpoints/call-ajax-leo
      {:endpoint (endpoints/start-cluster (:googleProject cluster) (:clusterName cluster))
       :headers ajax/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (this :-get-clusters-list)
                    (swap! state assoc :server-response {:server-error (get-parsed-response false)})))}))

   :-get-clusters-list
   (fn [{:keys [props state this]}]
     (endpoints/call-ajax-leo
      {:endpoint endpoints/get-clusters-list
       :headers ajax/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (let [filtered-clusters (filter (fn [c]
                                                      (and (= (get-in props [:workspace-id :namespace]) (:googleProject c))
                                                           (= (user/get-email) (:creator c)))) (get-parsed-response))
                          filtered-cluster-map (reduce merge (map (fn [c] {(:clusterName c) c}) filtered-clusters))]
                      ; Update the state with the current cluster list
                      (this :-update-cluster-map filtered-cluster-map)
                      ; If there are pending clusters, schedule another 'list clusters' call 10 seconds from now.
                      ; Note the refresh is still done for Running clusters because they may be auto-paused by Leo.
                      (when (notebook-utils/contains-statuses filtered-clusters ["Creating" "Updating" "Deleting" "Stopping" "Starting" "Running"])
                        (js/setTimeout #(this :-get-clusters-list) 10000))
                      ; If there are running clusters, call the /setCookie endpoint immediately.
                      (when (notebook-utils/contains-statuses filtered-clusters ["Running"])
                        (this :-process-running-clusters)))
                    (swap! state assoc :server-response {:server-error (get-parsed-response false)})))}))

   :-get-pet-token
   (fn [{:keys [props state this]}]
     (endpoints/call-ajax-sam
      {:endpoint (endpoints/pet-token (get-in props [:workspace-id :namespace]))
       :headers ajax/content-type=json
       :payload ["https://www.googleapis.com/auth/userinfo.email"
                 "https://www.googleapis.com/auth/userinfo.profile"
                 "https://www.googleapis.com/auth/devstorage.read_write"]
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (let [pet-token (get-parsed-response)]
                      (swap! state assoc :pet-token pet-token)
                      (this :-get-notebooks))
                    (swap! state assoc :server-response {:server-error (get-parsed-response false)})))}))

   :-get-notebooks
   (fn [{:keys [props state this]}]
     (let [bucket-name (get-in props [:workspace :workspace :bucketName])]
       (notebook-utils/get-notebooks-in-bucket bucket-name (:pet-token @state)
         (fn [{:keys [success? raw-response]}]
           (if success?
             (let [json-response (utils/parse-json-string raw-response true)
                   notebooks (filter (comp #(clojure.string/ends-with? % ".ipynb") :name) (:items json-response))]
               (swap! state assoc :server-response {:notebooks notebooks}))
             (swap! state assoc :server-response {:server-error raw-response}))))))

   :-update-cluster-map
   (fn [{:keys [state props this]} new-cluster-map]
     (let [{:keys [server-response]} @state
           {:keys [notebooks]} server-response]
       (when-not (= (:cluster-map @state) new-cluster-map)
         (swap! state assoc :cluster-map new-cluster-map)
         (when notebooks
           (swap! state assoc :server-response {:notebooks (map (fn [n]
                                                                  (if (contains? new-cluster-map (:cluster-name n))
                                                                    n
                                                                    (dissoc n :cluster-name)))
                                                                notebooks)})))))

   :-refresh-notebooks
   (fn [{:keys [props state this]}]
     (swap! state assoc :server-response {:notebooks nil})
     (this :-get-notebooks))

   :-assoc-cluster-with-notebook
   (fn [{:keys [state props this]} notebook cluster-name]
     (let [{:keys [server-response]} @state
           {:keys [notebooks]} server-response]
       (this :-get-clusters-list)
       (swap! state assoc :server-response {:notebooks (map (fn [n]
                                                              (if (= (:name n) (:name notebook))
                                                                (merge n {:cluster-name cluster-name})
                                                                n))
                                                            notebooks)})))})
