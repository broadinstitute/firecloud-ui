(ns broadfcui.page.workspace.notebooks.notebooks
  (:require
   [dmohs.react :as react]
   [broadfcui.utils :as utils]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.components.tab-bar :as tab-bar]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.utils.user :as user]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.common.input :as input]
   [broadfcui.components.foundation-tooltip :refer [FoundationTooltip]]
   [broadfcui.page.workspace.notebooks.create_cluster :refer [ClusterCreator]]
   [clojure.string :as string]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   ))

(defn- contains-statuses [clusters statuses]
  (seq (clojure.set/intersection (set statuses) (set (map :status clusters)))))

(defn- leo-notebook-url-base [cluster]
  (str (config/leonardo-url-root) "/notebooks/" (:googleProject cluster) "/" (:clusterName cluster)))

(defn- leo-notebook-url [cluster workspace-name notebook]
  (str (leo-notebook-url-base cluster) "/notebooks/" workspace-name "/" (last (clojure.string/split (:name notebook) #"/"))))

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
     (let [{:keys [show-cluster-choose-dialog?]} @state
           {:keys [notebooks cluster-map toolbar-items]} props]
       ;  (js/alert (str "cluster map is " cluster-map))
       [:div {}
        (when show-cluster-choose-dialog?
          [ClusterCreator (assoc props :dismiss #(swap! state dissoc :show-cluster-choose-dialog?)
                                       :clusters (vals cluster-map)
                                       :choose-cluster #(this :-choose-cluster %))])
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
             (fn [] (icons/render-icon {} :ellipsis-v))}
            {:header "Name" :initial-width 250
             :as-text :name :sort-by :name :sort-initial :asc
             :render
             (fn [notebook]
               (let [notebook-name (last (clojure.string/split (:name notebook) #"/"))]
                 notebook-name))}
            (table-utils/date-column {:header "Last Modified" :column-data :updated :style {}})
            {:id "cluster-menu" :initial-width 40
             :resizable? false :sortable? false :filterable? false :hidden? true
             :as-text :name :sort-by :name
             :render
             (fn [notebook]
               (dropdown/render-dropdown-menu
                {:label
                 (icons/render-icon {} :settings)
                 :width 85
                 :button-style {:height :auto :marginRight "0.5rem" :marginBottom "0.4rem"}
                 :items
                 (let [choose-link (links/create-internal
                                    {:data-test-id (str (:name notebook) "-chooser")
                                     :style {:textDecoration "none" :color (:button-primary style/colors)}
                                     :onClick #(swap! state assoc :show-cluster-choose-dialog? true :choose-notebook notebook)}
                                    "Choose...")]
                   (if (contains? notebook :cluster-name)
                     [{:text [:span {:style {:display "inline-flex"}} choose-link]}
                      {:text [:span {:style {:display "inline-flex"}} "Details"]}
                      {:text [:span {:style {:display "inline-flex"}} "Delete"]}]
                     [{:text [:span {:style {:display "inline-flex"}} choose-link]}]))}))}

            ;    (if-let [cluster (:cluster notebook)]
            ;     (links/create-internal
            ;       {:data-test-id (str (:name notebook) "-chooser")
            ;       :style {:textDecoration "none" :color (:button-primary style/colors)}
            ;       :onClick #(swap! state assoc :show-cluster-choose-dialog? true :choose-notebook notebook)}
            ;       (icons/render-icon {} :settings))))}
            {:header "Cluster" :initial-width 150
             :as-text :name :sort-by :name :sort-initial :asc
             :render
             (fn [notebook]
               (let [workspace-name (get-in props [:workspace :workspace :name])]
                 (if-let [cluster-name (:cluster-name notebook)]
                   (let [cluster (get cluster-map cluster-name)]
                     (if (= (:status cluster) "Running")
                       (links/create-external {:data-test-id (str (:clusterName cluster) "-link")
                                               :href (leo-notebook-url cluster workspace-name notebook)} (:clusterName cluster))
                       (:clusterName cluster)))
                   "<not set>")))}

                 ;    (links/create-internal
                 ;   {:data-test-id (str (:name notebook) "-chooser")
                 ;    :style {:textDecoration "none" :color (:button-primary style/colors)}
                 ;   :onClick #(swap! state assoc :show-cluster-choose-dialog? true :choose-notebook notebook)}
                 ;   "Choose...")))}
            {:header "Status" :initial-width 150
             :as-text :name :sort-by :name
             :render
             (fn [notebook]
               (if-let [cluster-name (:cluster-name notebook)]
                 (let [cluster (get cluster-map cluster-name)]
                   (this :-render-cluster-status cluster))))}]}
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
         "Running" [:div {:style {:display "inline-flex"}} (moncommon/render-success-icon) stop-cluster-link]
         "Stopped" start-cluster-link
         (moncommon/render-unknown-icon))
        [:span {:data-test-id clusterNameStatusId} (:status cluster)]]))

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
     (let [{:keys [server-response cluster-map]} @state
           {:keys [notebooks server-error]} server-response]
       [:div {:display "inline-flex"}
        [:div {} [:span {:data-test-id "notebooks-title" :style {:fontSize "125%" :fontWeight 500 :paddingBottom 10 :marginLeft 10}} "Notebooks"]]
        [:div {:style {:margin 10 :fontSize "88%"}} "These are your actual notebooks in the workspace"]
        (if server-error
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
               :cluster-map cluster-map
               :notebooks notebooks)]
            [:div {:style {:textAlign "center"}} (spinner "Loading notebooks...")]))]))

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
         (when (contains-statuses (vals cluster-map) ["Running"])
           (this :-process-running-clusters))
         (js/setTimeout #(this :-schedule-cookie-refresh) 120000))))

   :-process-running-clusters
   (fn [{:keys [props state this]}]
     (let [cluster-map (:cluster-map @state)
           running-clusters (filter (comp (partial = "Running") :status) (vals cluster-map))]
       (doseq [cluster running-clusters]
         (this :-localize-notebooks cluster)
         (ajax/call
          {:url (str (leo-notebook-url-base cluster) "/setCookie")
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
       ;  (js/alert (str "calling localize for cluster " (:clusterName cluster) " and " (count filtered-notebooks) " notebooks"))
       ;  (js/alert (reduce merge (conj (map (partial this :-localize-entry) filtered-notebooks) (this :-delocalize-json))))
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
     ;  (js/alert (str "in stop cluster with clsuter " (:googleProject cluster) (:clusterName cluster)))
     (swap! state assoc :cluster-map (assoc (:cluster-map @state) (:clusterName cluster) (assoc cluster :status "Stopping")))
     (endpoints/call-ajax-leo
      {:endpoint (endpoints/stop-cluster (:googleProject cluster) (:clusterName cluster))
       :headers ajax/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (let [cluster-map (:cluster-map @state)]
                      (this :-get-clusters-list))
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
                      (when-not (= (:cluster-map @state) filtered-cluster-map)
                        (swap! state assoc :cluster-map filtered-cluster-map))
                      ; If there are pending clusters, schedule another 'list clusters' call 10 seconds from now.
                      (when (contains-statuses filtered-clusters ["Creating" "Updating" "Deleting" "Stopping" "Starting"])
                        (js/setTimeout #(this :-get-clusters-list) 10000))
                      ; If there are running clusters, call the /setCookie endpoint immediately.
                      (when (contains-statuses filtered-clusters ["Running"])
                        (this :-process-running-clusters)))
                    (swap! state assoc :server-response {:server-error (get-parsed-response false)})))}))

   :-get-pet-token
   (fn [{:keys [props state this]}]
     (endpoints/call-ajax-sam
      {:endpoint (endpoints/get-pet-token (get-in props [:workspace-id :namespace]))
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
       (ajax/call {:url (str "https://www.googleapis.com/storage/v1/b/" bucket-name "/o?prefix=notebooks")
                   :method :get
                   :headers {"Authorization" (str "Bearer " (:pet-token @state))}
                   :on-done (fn [{:keys [success? raw-response]}]
                              (if success?
                                (let [json-response (utils/parse-json-string raw-response true)
                                      notebooks (filter (comp #(clojure.string/ends-with? % ".ipynb") :name) (:items json-response))]
                                  (swap! state assoc :server-response {:notebooks notebooks}))
                                  ;   (when (= 1 (count (:clusters @state)))
                                  ;    (doseq [notebook notebooks]
                                  ;      (this :-assoc-cluster-with-notebook notebook (first (:clusters @state))))))
                                (swap! state assoc :server-response {:server-error raw-response})))})))

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
