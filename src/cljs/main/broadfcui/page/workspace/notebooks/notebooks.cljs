(ns broadfcui.page.workspace.notebooks.notebooks
  (:require
   [dmohs.react :as react]
   [broadfcui.utils :as utils]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   ))

(react/defc NotebookCard
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [n]} props]
       [icons/notebook-icon]
       ;  [:div {} (str "notebook " n)]
       ))})

(defn- contains-statuses [clusters statuses]
  (seq (clojure.set/intersection (set statuses) (set (map :status clusters)))))

(react/defc NotebooksTable
  {:render
   (fn [{:keys [state props]}]
     (let [{:keys [notebooks toolbar-items]} props]
       [:div {}
        [Table
         {:data-test-id "notebooks-table" :data notebooks
          :body
          {:empty-message "There are no notebooks to display."
           :style table-style/table-light
           :fixed-column-count 1
           :column-defaults {"shown" ["Name" "Create Date" "Update Date" "Localized?"]}
           :columns
           [{:header "Name" :initial-width 250
             :as-text :name :sort-by :name :sort-initial :asc
             :render
             (fn [notebook]
               (last (clojure.string/split (:name notebook) #"/")))}
            (table-utils/date-column {:column-data :timeCreated :style {}})
            (table-utils/date-column {:header "Update Date" :column-data :updated :style {}})
            {:header "Localized?" :initial-width 250
             :as-text :localized?
             :render
             (fn [notebook]
               (str (:localized? notebook)))}]}
          :toolbar {:get-items (constantly toolbar-items)}}]]))})

(react/defc NotebooksContainer
  {:component-did-mount
   (fn [{:keys [this]}]
     (this :-get-pet-token))

   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [server-response]} @state
           {:keys [notebooks server-error]} server-response]
       [:div {:display "inline-flex"}
        [:div {} [:span {:data-test-id "notebooks-title" :style {:fontSize "125%" :fontWeight 500 :paddingBottom 10 :marginLeft 10}} "Notebooks"]]
        [:div {:style {:margin 10 :fontSize "88%"}} "These are your actual notebooks in the workspace"]
        (if server-error
          [comps/ErrorViewer {:data-test-id "notebooks-error" :error server-error}]
          (if notebooks
            [NotebooksTable (assoc props :notebooks notebooks)]
            [:div {:style {:textAlign "center"}} (spinner "Loading notebooks...")]))]))

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
                                  (swap! state assoc :server-response {:notebooks (map (partial merge {:localized? false}) notebooks)})
                                  (this :-get-clusters-list))
                                (swap! state assoc :server-response {:server-error raw-response})))})))

   :-get-clusters-list
   (fn [{:keys [props state this]}]
     (endpoints/call-ajax-leo
      {:endpoint endpoints/get-clusters-list
       :headers ajax/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (let [filtered-clusters (filter #(= (get-in props [:workspace-id :namespace]) (:googleProject %)) (get-parsed-response))]
                      (when-not (= (:clusters @state) filtered-clusters)
                        (swap! state assoc :clusters filtered-clusters))
                      (when (contains-statuses filtered-clusters ["Running"])
                        (this :-localize-notebooks)))
                    (swap! state assoc :server-response {:server-error (get-parsed-response false)})))}))

   :-localize-notebooks
   (fn [{:keys [props state]}]
     (let [{:keys [server-response clusters]} @state
           running-clusters (filter (comp (partial = "Running") :status) clusters)
           {:keys [notebooks]} server-response
           notebooks-to-localize (filter (comp (partial not) :localized?) notebooks)]
       (doseq [notebook notebooks-to-localize]
         (doseq [cluster running-clusters]
           ; (js/alert (str "calling localize for cluster " (:clusterName cluster) " and notebook " (:name notebook)))
           (endpoints/localize-notebook (:googleProject cluster) (:clusterName cluster)
                                        {:foo "bar"} ; todo
                                        (fn [{:keys [success? get-parsed-response]}]
                                          (if success?
                                            (swap! state assoc :server-response {:notebooks (map (fn [n]
                                                                                                   (if (= (:name n) (:name notebook))
                                                                                                     (merge n {:localized? true})
                                                                                                    n)))})
                                          ; (swap! state assoc :server-response {:server-error (get-parsed-response false)})
                                          )))))))})
