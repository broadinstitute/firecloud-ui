(ns broadfcui.page.workspace.notebooks.rename_notebook
  (:require
   [dmohs.react :as react]
   [broadfcui.components.modals :as modals]
   [broadfcui.common.style :as style]
   [broadfcui.common.input :as input]
   [broadfcui.common.components :as comps]
   [broadfcui.page.workspace.notebooks.utils :as notebook-utils]
   [broadfcui.components.blocker :refer [blocker]]
   ))

(react/defc NotebookRenamer
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [renaming? server-response validation-errors]} @state
           {:keys [server-error]} server-response
           {:keys [choose-notebook]} props]
       [modals/OKCancelForm
        {:header "Rename Notebook"
         :dismiss (:dismiss props)
         :ok-button {:text "Rename"
                     :onClick #(this :-rename-notebook)}
         :content
         (react/create-element
          [:div {:style {:marginTop 0}}
           (when renaming? (blocker "Renaming notebook..."))
           [comps/ErrorViewer {:error server-error}]

           (notebook-utils/create-inline-form-label (str "Enter new name for \"" (notebook-utils/notebook-name choose-notebook) \"":"))
           [input/TextField {:data-test-id "notebook-name-input" :ref "newNotebookName" :autoFocus true :style {:width "100%"}
                             :defaultValue "" :predicates [(input/nonempty "Notebook name") (input/alphanumeric_-space "Notebook name")]}]
           (style/create-validation-error-message validation-errors)])}]))

   :-rename-notebook
   (fn [{:keys [props state this refs]} choose-notebook]
     (let [{:keys [choose-notebook pet-token]} props
           bucket-name (get-in props [:workspace :workspace :bucketName])
           [new-notebook-name & fails] (input/get-and-validate refs "newNotebookName")]
       (if fails
         (swap! state assoc :validation-errors fails)
         (do
           (swap! state assoc :renaming? true)
           (notebook-utils/copy-notebook bucket-name pet-token choose-notebook (str new-notebook-name ".ipynb")
             (fn [{:keys [success? raw-response]}]
               (if success?
                 (notebook-utils/delete-notebook bucket-name pet-token choose-notebook
                   (fn [{:keys [success? raw-response]}]
                     (swap! state assoc :renaming? false)
                     (if success?
                       (do
                         ((:rename-notebook props))
                         ((:dismiss props)))
                       (swap! state assoc :server-response {:server-error raw-response}))))
                 (swap! state assoc :server-response {:server-error raw-response}))))))))})