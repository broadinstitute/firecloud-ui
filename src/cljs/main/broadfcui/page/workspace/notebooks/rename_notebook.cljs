(ns broadfcui.page.workspace.notebooks.rename_notebook
  (:require
   [dmohs.react :as react]
   [broadfcui.components.modals :as modals]
   [broadfcui.common.style :as style]
   [broadfcui.common.input :as input]
   [broadfcui.common.components :as comps]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.foundation-tooltip :refer [FoundationTooltip]]
   [broadfcui.page.workspace.notebooks.utils :as notebook-utils]
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
         :ok-button {:text "Rename" :onClick #(this :-rename-notebook)}
         :content
         (react/create-element
          [:div {:style {:marginTop 0}}
           (when renaming? (blocker "Renaming notebook..."))
           [comps/ErrorViewer {:error server-error}]

           [:div {:style {:width "48%" :marginRight "4%" :marginBottom "1%"}}
            [FoundationTooltip {:text (notebook-utils/create-inline-form-label "Name")
                                :tooltip (str "Enter new name for notebook \"" (notebook-utils/notebook-name choose-notebook) \" "")}]]
           [input/TextField {:data-test-id "notebook-name-input" :ref "newNotebookName" :autoFocus true :style {:width "100%"}
                             :defaultValue (notebook-utils/notebook-name choose-notebook) :predicates [(input/nonempty "Notebook name") (input/alphanumeric_-space "Notebook name")]}]
           (style/create-validation-error-message validation-errors)])}]))

   :-rename-notebook
   (fn [{:keys [props state this refs]}]
     (let [{:keys [choose-notebook pet-token notebooks]} props
           bucket-name (get-in props [:workspace :workspace :bucketName])
           [new-notebook-name & fails] (input/get-and-validate refs "newNotebookName")]
       (if fails
         (swap! state assoc :validation-errors fails)
         (if (= (notebook-utils/notebook-name choose-notebook) new-notebook-name)
           ((:dismiss props)) ; no-op if the name is unchanged
           (if (some (comp (partial = new-notebook-name) #(notebook-utils/notebook-name %)) notebooks)
             (swap! state assoc :validation-errors [(str "Notebook with name \"" new-notebook-name "\" already exists")]) ; fail if a notebook already exists with the same name
             (do
               (swap! state assoc :renaming? true)
               ; a rename is a copy plus a delete
               (notebook-utils/copy-notebook bucket-name pet-token choose-notebook new-notebook-name
                                             (fn [{:keys [success? raw-response]}]
                                               (if success?
                                                 (notebook-utils/delete-notebook bucket-name pet-token choose-notebook
                                                                                 (fn [{:keys [success? raw-response]}]
                                                                                   (swap! state assoc :renaming? false)
                                                                                   (if success?
                                                                                     (do
                                                                                       ((:refresh-notebooks props))
                                                                                       ((:dismiss props)))
                                                                                     (swap! state assoc :server-response {:server-error raw-response}))))
                                                 (swap! state assoc :server-response {:server-error raw-response}))))))))))})