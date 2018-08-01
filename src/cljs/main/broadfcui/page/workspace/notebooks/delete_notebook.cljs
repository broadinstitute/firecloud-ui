(ns broadfcui.page.workspace.notebooks.delete_notebook
  (:require
   [dmohs.react :as react]
   [broadfcui.components.modals :as modals]
   [broadfcui.common.style :as style]
   [broadfcui.common.input :as input]
   [broadfcui.common.components :as comps]
   [broadfcui.page.workspace.notebooks.utils :as notebook-utils]
   [broadfcui.components.blocker :refer [blocker]]
   ))

(react/defc NotebookDeleter
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [deleting? server-response validation-errors]} @state
           {:keys [server-error]} server-response
           {:keys [choose-notebook]} props]
       [modals/OKCancelForm
        {:header "Delete Notebook"
         :dismiss (:dismiss props)
         :ok-button {:text "Delete"
                     :onClick #(this :-delete-notebook)}
         :content
         (react/create-element
          [:div {:style {:marginTop 0}}
           (when deleting? (blocker "Deleting notebook..."))
           [:div {} (str "Are you sure you want to delete notebook " (notebook-utils/notebook-name choose-notebook) "?")]
           [comps/ErrorViewer {:error server-error}]])}]))

   :-delete-notebook
   (fn [{:keys [props state this refs]} choose-notebook]
     (let [{:keys [choose-notebook pet-token]} props
           bucket-name (get-in props [:workspace :workspace :bucketName])]
       (swap! state assoc :deleting? true)
       (notebook-utils/delete-notebook bucket-name pet-token choose-notebook
         (fn [{:keys [success? raw-response]}]
           (if success?
             (do
               ((:delete-notebook props))
               ((:dismiss props))))
           (swap! state assoc :server-response {:server-error raw-response})))))})