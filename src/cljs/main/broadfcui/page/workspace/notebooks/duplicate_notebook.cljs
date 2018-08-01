(ns broadfcui.page.workspace.notebooks.duplicate_notebook
  (:require
   [dmohs.react :as react]
   [broadfcui.components.modals :as modals]
   [broadfcui.common.style :as style]
   [broadfcui.common.input :as input]
   [broadfcui.common.components :as comps]
   [broadfcui.page.workspace.notebooks.utils :as notebook-utils]
   [broadfcui.components.blocker :refer [blocker]]
   ))

(react/defc NotebookDuplicator
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [duplicating? server-response validation-errors]} @state
           {:keys [server-error]} server-response
           {:keys [choose-notebook]} props]
       [modals/OKCancelForm
        {:header "Duplicate Notebook"
         :dismiss (:dismiss props)
         :ok-button {:text "Duplicate"
                     :onClick #(this :-duplicate-notebook)}
         :content
         (react/create-element
          [:div {:style {:marginTop 0}}
           (when duplicating? (blocker "Duplicating notebook..."))
           [comps/ErrorViewer {:error server-error}]

           (notebook-utils/create-inline-form-label (str "Enter new name for \"" (notebook-utils/notebook-name choose-notebook) \"":"))
           [input/TextField {:data-test-id "notebook-name-input" :ref "newNotebookName" :autoFocus true :style {:width "100%"}
                             :defaultValue "" :predicates [(input/nonempty "Notebook name") (input/alphanumeric_-space "Notebook name")]}]
           (style/create-validation-error-message validation-errors)])}]))

   :-duplicate-notebook
   (fn [{:keys [props state this refs]} choose-notebook]
     (let [{:keys [choose-notebook pet-token]} props
           bucket-name (get-in props [:workspace :workspace :bucketName])
           [new-notebook-name & fails] (input/get-and-validate refs "newNotebookName")]
       (if fails
         (swap! state assoc :validation-errors fails)
         (do
           (swap! state assoc :duplicating? true)
           (notebook-utils/copy-notebook bucket-name pet-token choose-notebook (str new-notebook-name ".ipynb")
             (fn [{:keys [success? raw-response]}]
               (if success?
                 (do
                   ((:duplicate-notebook props))
                   ((:dismiss props)))
                 (swap! state assoc :server-response {:server-error raw-response}))))))))})