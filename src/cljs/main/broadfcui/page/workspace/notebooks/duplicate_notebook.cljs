(ns broadfcui.page.workspace.notebooks.duplicate_notebook
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

(react/defc NotebookDuplicator
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [duplicating? server-response validation-errors]} @state
           {:keys [server-error]} server-response
           {:keys [choose-notebook]} props]
       [modals/OKCancelForm
        {:header "Duplicate Notebook"
         :dismiss (:dismiss props)
         :ok-button {:text "Duplicate" :onClick #(this :-duplicate-notebook)}
         :content
         (react/create-element
          [:div {:style {:marginTop 0}}
           (when duplicating? (blocker "Duplicating notebook..."))
           [comps/ErrorViewer {:error server-error}]

           [:div {:style {:width "48%" :marginRight "4%" :marginBottom "1%"}}
            [FoundationTooltip {:text (notebook-utils/create-inline-form-label "Name")
                                :tooltip (str "Enter new name for copy of notebook \"" (notebook-utils/notebook-name choose-notebook) \""")}]]
           [input/TextField {:data-test-id "notebook-name-input" :ref "newNotebookName" :autoFocus true :style {:width "100%"}
                             :defaultValue (str "Copy of " (notebook-utils/notebook-name choose-notebook)) :predicates [(input/nonempty "Notebook name") (input/alphanumeric_-space "Notebook name")]}]
           (style/create-validation-error-message validation-errors)])}]))

   :-duplicate-notebook
   (fn [{:keys [props state this refs]}]
     (let [{:keys [choose-notebook pet-token notebooks]} props
           bucket-name (get-in props [:workspace :workspace :bucketName])
           [new-notebook-name & fails] (input/get-and-validate refs "newNotebookName")]
       (if fails
         (swap! state assoc :validation-errors fails)
         ; no-op if the name is unchanged
         (if (= (notebook-utils/notebook-name choose-notebook) new-notebook-name)
           ((:dismiss props))
           ; fail if a notebook already exists with the same name
           (if (some (comp (partial = new-notebook-name) #(notebook-utils/notebook-name %)) notebooks)
             (swap! state assoc :validation-errors [(str "Notebook with name \"" new-notebook-name "\" already exists")])
             (do
               (swap! state assoc :duplicating? true)
               (notebook-utils/copy-notebook bucket-name pet-token choose-notebook new-notebook-name
                 (fn [{:keys [success? raw-response]}]
                   (swap! state assoc :duplicating? false)
                   (if success?
                     (do
                       ((:refresh-notebooks props))
                       ((:dismiss props)))
                     (swap! state assoc :server-response {:server-error raw-response}))))))))))})