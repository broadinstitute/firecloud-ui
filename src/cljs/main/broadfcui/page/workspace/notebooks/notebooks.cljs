(ns broadfcui.page.workspace.notebooks.notebooks
  (:require
   [dmohs.react :as react]
   [broadfcui.utils :as utils]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
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
           :column-defaults {"shown" ["Name"]}
           :columns
           [{:header "Name" :initial-width 250
             :as-text :notebookName :sort-by :notebookName :sort-initial :asc
             :render
             (fn [notebook]
               (:notebookName notebook))}]}
          :toolbar {:get-items (constantly toolbar-items)}}]]))})

(react/defc NotebooksContainer
  {:component-did-mount
   (fn [{:keys [this]}]
     (this :-get-pet-token))

   :render
   (fn [{:keys [props state]}]
     [:div {:display "inline-flex"}
      [:div {} [:span {:data-test-id "spark-clusters-title" :style {:fontSize "125%" :fontWeight 500 :paddingBottom 10 :marginLeft 10}} "Notebooks"]]
      [:div {:style {:margin 10 :fontSize "88%"}} "These are your actual notebooks in the workspace"]
      [NotebooksTable (assoc props :notebooks (map (fn [n] {:notebookName (str "notebook " n)}) (range 10)))]])

   :-get-pet-token
   (fn [{:keys [props state this]}]
     (endpoints/call-ajax-sam
      {:endpoint (endpoints/get-pet-token (get-in props [:workspace-id :namespace]))
       :headers ajax/content-type=json
       :payload ["https://www.googleapis.com/auth/userinfo.email" "https://www.googleapis.com/auth/userinfo.profile"]
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (let [pet-token (get-parsed-response)]
                      (swap! state assoc :pet-token pet-token)
                      (this :-get-notebooks))
                    (swap! state assoc :server-response {:server-error (get-parsed-response false)})))}))
   :-get-notebooks
   (fn [{:keys [props state]}]
     (let [bucket-name (get-in props [:workspace :workspace :bucketName])]
       (ajax/call {:url (str "https://www.googleapis.com/storage/v1/b/" bucket-name "/o?prefix=notebooks")
                   :method :get
                   :headers {"Authorization" (str "Bearer " (:pet-token @state))}
                   :on-done (fn [{:keys [raw-response]}]
                              (let [json-response (utils/parse-json-string raw-response true)
                                    notebooks (filter (comp #(clojure.string/ends-with? % ".ipynb") :name) (:items json-response))]
                                (js/alert raw-response)
                                (js/alert (map :name notebooks))))})))})
