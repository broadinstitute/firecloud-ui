(ns broadfcui.page.workspace.notebooks.delete-notebook
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.modals :as modals]
   [broadfcui.page.workspace.notebooks.utils :as notebook-utils]
   [broadfcui.utils :as utils]
   ))

(react/defc NotebookDeleter
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [deleting? server-response validation-errors]} @state
           {:keys [server-error]} server-response
           {:keys [choose-notebook dismiss]} props]
       [modals/OKCancelForm
        {:header "Delete Notebook"
         :dismiss dismiss
         :ok-button {:text "Delete" :onClick #(this :-delete-notebook)}
         :content
         (react/create-element
          [:div {:style {:marginTop 0 :width 500}}
           (when deleting? (blocker "Deleting notebook..."))
           [:div {:style {:marginBottom "1em"}} (str "Are you sure you want to delete notebook \""
                         (notebook-utils/notebook-name choose-notebook) "\"? This operation cannot be undone.")]
           [comps/ErrorViewer {:error server-error}]])}]))

   :-delete-notebook
   (fn [{:keys [props state this refs]} choose-notebook]
     (let [{:keys [choose-notebook pet-token refresh-notebooks dismiss]} props
           bucket-name (get-in props [:workspace :workspace :bucketName])]
       (swap! state assoc :deleting? true)
       (notebook-utils/delete-notebook bucket-name pet-token choose-notebook
                                       (fn [{:keys [success? raw-response]}]
                                         (swap! state assoc :deleting? false)
                                         ; (js/alert (get raw-response "message"))
                                         (if success?
                                           (do
                                             (refresh-notebooks)
                                             (dismiss))
                                           (swap! state assoc :server-response {:server-error (notebook-utils/parse-gcs-error raw-response)}))))))})
