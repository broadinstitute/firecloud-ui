(ns broadfcui.page.workspace.notebooks.notebooks-table
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.page.workspace.notebooks.choose-cluster :refer [ChooseClusterViewer]]
   [broadfcui.page.workspace.notebooks.cluster-details :refer [ClusterDetailsViewer]]
   [broadfcui.page.workspace.notebooks.cluster-error :refer [ClusterErrorViewer]]
   [broadfcui.page.workspace.notebooks.create-cluster :refer [ClusterCreator]]
   [broadfcui.page.workspace.notebooks.create-notebook :refer [NotebookCreator]]
   [broadfcui.page.workspace.notebooks.delete-cluster :refer [ClusterDeleter]]
   [broadfcui.page.workspace.notebooks.delete-notebook :refer [NotebookDeleter]]
   [broadfcui.page.workspace.notebooks.duplicate-notebook :refer [NotebookDuplicator]]
   [broadfcui.page.workspace.notebooks.rename-notebook :refer [NotebookRenamer]]
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
     (let [{:keys [choose-notebook choose-cluster]} @state
           {:keys [notebooks cluster-map pet-token toolbar-items]} props]
       [:div {}
        (modals/show-modals
         state
         {:show-cluster-choose-dialog?
          [ChooseClusterViewer (assoc props :choose-notebook choose-notebook
                                            :reload-after-choose #(this :-choose-cluster %)
                                            :show-create-cluster #(swap! state assoc :show-cluster-create-dialog? true))]
          :show-cluster-create-dialog?
          [ClusterCreator (assoc props :choose-notebook choose-notebook
                                       :reload-after-choose #(this :-choose-cluster %))]
          :show-cluster-delete-dialog?
          [ClusterDeleter (assoc props :cluster-to-delete choose-cluster)]
          :show-cluster-details-dialog?
          [ClusterDetailsViewer (assoc props :cluster-to-display-details choose-cluster)]
          :show-error-dialog?
          [ClusterErrorViewer (assoc props :cluster-to-view choose-cluster)]
          :show-notebook-rename-dialog?
          [NotebookRenamer (assoc props :choose-notebook choose-notebook)]
          :show-notebook-duplicate-dialog?
          [NotebookDuplicator (assoc props :choose-notebook choose-notebook)]
          :show-notebook-delete-dialog?
          [NotebookDeleter (assoc props :choose-notebook choose-notebook)]})

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
             :render notebook-utils/notebook-name}
            (table-utils/date-column {:header "Last Modified" :column-data :updated :style {}})
            {:id "cluster-menu" :initial-width 30
             :resizable? false :sortable? false :filterable? false :hidden? true
             :as-text :name :sort-by :name
             :render
             (fn [notebook]
               (when (contains? cluster-map (get notebook :cluster-name))
                 (dropdown/render-dropdown-menu
                  {:label
                   (icons/render-icon {} :settings)
                   :width 100
                   :button-style {:height :auto :marginRight "0.5rem" :marginBottom "0.4rem"}
                   :items
                   (filter (comp not nil?)
                           ; Details - always displayed when there is an associated cluster
                           [{:text [:span {:style {:display "inline-flex"}} "Details"]
                             :dismiss #(swap! state assoc :show-cluster-details-dialog? true :choose-cluster (:cluster-name notebook))}
                            ; Choose - always displayed when there is an associated cluster
                            {:text [:span {:style {:display "inline-flex"}} "Choose..."]
                             :dismiss #(swap! state assoc :show-cluster-choose-dialog? true :choose-notebook notebook)}
                            ; Delete - displayed when the associated cluster is in Running or Stopped status
                            (when (notebook-utils/contains-statuses [(get cluster-map (:cluster-name notebook))] ["Running" "Error" "Stopped"])
                              {:text [:span {:style {:display "inline-flex"}} "Delete"]
                               :dismiss #(swap! state assoc :show-cluster-delete-dialog? true :choose-cluster (:cluster-name notebook))})])})))}
            {:header "Cluster" :initial-width 150
             :as-text :name :sort-by :name :sort-initial :asc
             :render
             (fn [notebook]
               ; If there is an associated cluster, display it.
               (if-let [cluster (get cluster-map (:cluster-name notebook))]
                 ; If the associated cluster is Running, display the name as a link.
                 (if (= (:status cluster) "Running")
                   (let [workspace-name (get-in props [:workspace :workspace :name])]
                     (links/create-external {:data-test-id (str (:clusterName cluster) "-link")
                                             :href (notebook-utils/leo-notebook-url cluster workspace-name notebook)} (:clusterName cluster)))
                   (:clusterName cluster))
                 ; If there are no clusters, display a Create link. Otherwise, display a Choose link.
                 (if (empty? cluster-map)
                   (links/create-internal
                     {:data-test-id (str (:name notebook) "-creator")
                      :style {:textDecoration "none" :color (:button-primary style/colors)}
                      :onClick #(swap! state assoc :show-cluster-create-dialog? true :choose-notebook notebook)}
                     "Create...")
                   (links/create-internal
                     {:data-test-id (str (:name notebook) "-chooser")
                      :style {:textDecoration "none" :color (:button-primary style/colors)}
                      :onClick #(swap! state assoc :show-cluster-choose-dialog? true :choose-notebook notebook)}
                   "Choose..."))))}
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
           {:keys [cluster-map stop-cluster start-cluster]} props
           stop-cluster-link (links/create-internal {:data-test-id clusterNameStatusId
                                                     :style {:textDecoration "none" :color (:button-primary style/colors)}
                                                     :onClick #(stop-cluster cluster)}
                               pause-icon)
           start-cluster-link (links/create-internal {:data-test-id clusterNameStatusId
                                                      :style {:textDecoration "none" :color (:button-primary style/colors)}
                                                      :onClick #(start-cluster cluster)}
                                play-icon)
           view-error-link (links/create-internal {:data-test-id clusterNameStatusId
                                                   :style {:textDecoration "none" :color (:button-primary style/colors)}
                                                   :onClick #(swap! state assoc :show-error-dialog? true :choose-cluster cluster)}
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
     (let [{:keys [choose-notebook]} @state
           {:keys [choose-cluster]} props]
       (choose-cluster choose-notebook selected-cluster)))})

(react/defc NotebooksContainer
  {:get-initial-state
   (fn []
     {:file-input-key (gensym "file-input-")})

   :refresh
   (fn [{:keys [this]}]
     (this :-get-pet-token)
     (this :-get-clusters-list)
     (this :-schedule-cookie-refresh))

   :render
   (fn [{:keys [props state this refs]}]
     (let [{:keys [server-response cluster-map show-create-dialog? uploading? upload-error pet-token file-input-key]} @state
           {:keys [notebooks server-error]} server-response]
       [:div {:display "inline-flex"}
        (when show-create-dialog?
          [NotebookCreator (assoc props :dismiss #(swap! state dissoc :show-create-dialog?)
                                        :pet-token pet-token
                                        :notebooks notebooks
                                        :refresh-notebooks #(this :-refresh-notebooks))])
        [:div {} [:span {:data-test-id "notebooks-title" :style {:fontSize "125%" :fontWeight 500 :paddingBottom 10 :marginLeft 10}} "Notebooks"]]
        [:div {:style {:margin 10 :fontSize "88%"}}
         "Launch an interactive analysis environment based on Jupyter notebooks, Spark, and Hail.
          This beta feature is under active development. See documentation " [:a {:href (config/user-notebooks-guide-url) :target "_blank"} "here" icons/external-link-icon]]
        [comps/ErrorViewer {:data-test-id "notebooks-error" :error server-error}]

        ;; Upload notebook
        ;; This key is changed every time a file is selected causing React to completely replace the
        ;; element. Otherwise, if a user selects the same file (even after having modified it), the
        ;; browser will not fire the onChange event.
        [:input {:key file-input-key
                 :type "file"
                 :ref "notebook-uploader"
                 :style {:display "none"}
                 :onChange (fn [e]
                             (let [file (-> e .-target .-files (aget 0))
                                   reader (js/FileReader.)]
                               (when file
                                 (set! (.-onload reader)
                                       #(let [name (.-name file)
                                              text (.-result reader)]
                                          (swap! state assoc :file-input-key (gensym "notebook-uploader-"))
                                          (this :-upload-notebook name text)))
                                 (.readAsText reader file))))}]
        (if notebooks
          [NotebooksTable
           (assoc props
             :toolbar-items [flex/spring [buttons/Button {:data-test-id "create-modal-button"
                                                          :text "Create Notebook..." :style {:marginRight 1}
                                                          :onClick #(swap! state assoc :show-create-dialog? true)}]
                             [:div {} [buttons/Button {:data-test-id "upload-modal-button"
                                                       :text "Upload Notebook..." :style {:marginRight 1}
                                                       :onClick #(-> (@refs "notebook-uploader") .click)}]]]
             :choose-cluster #(this :-assoc-cluster-with-notebook %1 %2)
             :stop-cluster #(this :-stop-cluster %)
             :start-cluster #(this :-start-cluster %)
             :reload-after-delete #(this :-get-clusters-list %)
             :cluster-map cluster-map
             :notebooks notebooks
             :pet-token pet-token
             :refresh-notebooks #(this :-refresh-notebooks))]
          [:div {:style {:textAlign "center"}} (spinner "Loading notebooks...")])

        (when uploading?
          (blocker "Uploading notebook..."))
        (when upload-error
          (let [dismiss #(swap! state dissoc :upload-error)]
            [modals/OKCancelForm
             {:header "Upload Error"
              :dismiss dismiss
              :ok-button {:text "Close" :onClick dismiss}
              :show-cancel? false
              :content
              (react/create-element
               (style/create-flexbox {}
                                     [:span {:style {:paddingRight "0.5rem"}}
                                      (icons/render-icon {:style {:color (:state-exception style/colors)}}
                                                         :warning)]
                                     upload-error))}]))]))

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
     (let [{:keys [cluster-map]} @state]
       (when-not (:dead? @locals)
         (when (notebook-utils/contains-statuses (vals cluster-map) ["Running"])
           (this :-process-running-clusters))
         (js/setTimeout #(this :-schedule-cookie-refresh) 120000))))

   ; For all Running clusters, calls /setCookie and localizes associated notebooks.
   :-process-running-clusters
   (fn [{:keys [props state this]}]
     (let [{:keys [cluster-map]} @state
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
     (let [{:keys [workspsaceName]} props
           {:keys [server-response]} @state
           {:keys [notebooks]} server-response
           notebooks-to-localize (filter (every-pred (comp (partial = (:clusterName cluster)) :cluster-name)
                                                     (comp false? :localized?))
                                         notebooks)]
       (endpoints/localize-notebook (:googleProject cluster) (:clusterName cluster)
                                    (reduce merge (conj (map (partial this :-localize-entry) notebooks-to-localize) (this :-delocalize-json)))
                                    (fn [{:keys [success? get-parsed-response]}]
                                      (if success?
                                        ; flip :localized? to true so we don't re-localize notebooks to this cluster
                                        (swap! state assoc :server-response {:notebooks (map (fn [n]
                                                                                               (if (= (:cluster-name n) (:clusterName cluster))
                                                                                                 (merge n {:localized? true})
                                                                                                 n))
                                                                                             notebooks)})
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
     (let [{:keys [cluster-map]} @state]
       (swap! state assoc :cluster-map (assoc cluster-map (:clusterName cluster) (assoc cluster :status "Stopping")))
       (endpoints/call-ajax-leo
        {:endpoint (endpoints/stop-cluster (:googleProject cluster) (:clusterName cluster))
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (if success?
                      (this :-get-clusters-list)
                      (swap! state assoc :server-response {:server-error (get-parsed-response false)})))})))

   :-start-cluster
   (fn [{:keys [props state this]} cluster]
     (let [{:keys [cluster-map]} @state]
       (swap! state assoc :cluster-map (assoc cluster-map (:clusterName cluster) (assoc cluster :status "Starting")))
       (endpoints/call-ajax-leo
        {:endpoint (endpoints/start-cluster (:googleProject cluster) (:clusterName cluster))
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (if success?
                      (this :-get-clusters-list)
                      (swap! state assoc :server-response {:server-error (get-parsed-response false)})))})))

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
                      (when (notebook-utils/contains-statuses filtered-clusters ["Creating" "Updating" "Deleting" "Stopping" "Starting"])
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

   ; Gets notebooks from GCS
   :-get-notebooks
   (fn [{:keys [props state this]}]
     (let [bucket-name (get-in props [:workspace :workspace :bucketName])
           {:keys [pet-token]} @state]
       (notebook-utils/get-notebooks-in-bucket bucket-name pet-token
                                               (fn [{:keys [success? raw-response]}]
                                                 (if success?
                                                   (let [json-response (utils/parse-json-string raw-response true)
                                                         notebooks (filter (comp #(clojure.string/ends-with? % ".ipynb") :name) (:items json-response))]
                                                     (this :-load-notebook-cluster-association notebooks)
                                                     (swap! state assoc :server-response {:notebooks notebooks}))
                                                   (swap! state assoc :server-response {:server-error raw-response}))))))

   :-update-cluster-map
   (fn [{:keys [state props this]} new-cluster-map]
     (let [{:keys [server-response cluster-map]} @state
           {:keys [notebooks]} server-response]
       (when-not (= cluster-map new-cluster-map)
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
           {:keys [notebooks]} server-response
           updated-notebooks (map (fn [n]
                                    (if (= (:name n) (:name notebook))
                                      (merge n {:cluster-name cluster-name :localized? false})
                                      n))
                                  notebooks)]
       (this :-get-clusters-list)
       (swap! state assoc :server-response {:notebooks updated-notebooks})
       (this :-persist-notebook-cluster-association updated-notebooks)))

   :-upload-notebook
   (fn [{:keys [state props this]} name text]
     (let [{:keys [server-response pet-token]} @state
           {:keys [notebooks]} server-response
           bucket-name (get-in props [:workspace :workspace :bucketName])]
       (if (clojure.string/ends-with? name ".ipynb")
         (if (not-any? (comp (partial = name) #(notebook-utils/notebook-name-with-suffix %)) notebooks)
           (do
             (swap! state assoc :uploading? true)
             (notebook-utils/upload-notebook bucket-name pet-token name text
                                             (fn [{:keys [success? raw-response]}]
                                               (swap! state dissoc :uploading?)
                                               (if success?
                                                 (this :-get-notebooks)
                                                 (swap! state assoc :server-response {:server-error raw-response})))))
           (swap! state assoc :upload-error (str "Error uploading notebook \"" name "\": notebook already exists in this workspace.")))
         (swap! state assoc :upload-error (str "Error uploading notebook \"" name "\": file name must end with .ipynb.")))))

   ; Persists the notebook-cluster associations in a special config file in GCS so they persist between page loads.
   :-persist-notebook-cluster-association
   (fn [{:keys [state props this]} notebooks]
     (let [{:keys [notebook-config cluster-map pet-token]} @state
           bucket-name (get-in props [:workspace :workspace :bucketName])
           notebook-config-to-add (reduce conj (map (fn [n]
                                                      (if-let [cluster-name (:cluster-name n)]
                                                        {(notebook-utils/notebook-name n) [(select-keys n [:cluster-name :localized?])]}
                                                        {}))
                                                    notebooks))
           new-notebook-config (merge-with (fn [a b] (into (filter #(not (contains? cluster-map (:cluster-name %))) a) b)) notebook-config notebook-config-to-add)
           json (utils/->json-string new-notebook-config)]
       (notebook-utils/update-notebook-config-in-bucket bucket-name pet-token json
                                                        (fn [{:keys [success? raw-response]}]
                                                          (when-not success?
                                                            (swap! state assoc :server-response {:server-error raw-response}))))))

   ; Loads the notebook-cluster association preferences from GCS.
   :-load-notebook-cluster-association
   (fn [{:keys [state props this]} notebooks]
     (let [bucket-name (get-in props [:workspace :workspace :bucketName])
           {:keys [cluster-map pet-token]} @state]
       (notebook-utils/get-notebook-config-in-bucket bucket-name pet-token
                                                     (fn [{:keys [success? raw-response]}]
                                                       (if success?
                                                         (let [unescaped-json (utils/parse-json-string raw-response false)
                                                               notebook-config (utils/parse-json-string unescaped-json false)
                                                               new-notebooks (map (fn [n]
                                                                                    (if-let [nc (get notebook-config (notebook-utils/notebook-name n))]
                                                                                      (let [associated-cluster (first (filter (comp (partial contains? cluster-map) #(get % "cluster-name")) nc))
                                                                                            cluster-name (get associated-cluster "cluster-name")
                                                                                            localized? (get associated-cluster "localized?")]
                                                                                        (merge n {:cluster-name cluster-name :localized? localized?}))
                                                                                      n))
                                                                                  notebooks)]
                                                           (utils/multi-swap! state (assoc :notebook-config notebook-config) (assoc :server-response {:notebooks new-notebooks})))
                                                         (swap! state assoc :notebook-config {}))))))})
