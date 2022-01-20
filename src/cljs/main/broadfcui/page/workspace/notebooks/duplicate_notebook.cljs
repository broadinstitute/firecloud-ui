(ns broadfcui.page.workspace.notebooks.duplicate-notebook
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.foundation-tooltip :refer [FoundationTooltip]]
   [broadfcui.components.modals :as modals]
   [broadfcui.page.workspace.notebooks.utils :as notebook-utils]
   [broadfcui.utils :as utils]
   ))

(react/defc NotebookDuplicator
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [duplicating? server-response validation-errors]} @state
           {:keys [server-error]} server-response
           {:keys [choose-notebook dismiss]} props]
       [modals/OKCancelForm
        {:header "Duplicate Notebook"
         :dismiss dismiss
         :ok-button {:text "Duplicate" :onClick #(this :-duplicate-notebook)}
         :content
         (react/create-element
          [:div {:style {:marginTop 0 :width 500}}
           (when duplicating? (blocker "Duplicating notebook..."))
           [:div {:style {:width "48%" :marginRight "4%" :marginBottom "1%"}}
            [FoundationTooltip {:text (notebook-utils/create-inline-form-label "Name")
                                :tooltip (str "Enter new name for copy of notebook \"" (notebook-utils/notebook-name choose-notebook) \" "")}]]
           [input/TextField {:data-test-id "notebook-name-input" :ref "newNotebookName" :autoFocus true :style {:width "100%"}
                             :defaultValue (str "Copy of " (notebook-utils/notebook-name choose-notebook))
                             :predicates [(input/nonempty "Notebook name") (input/alphanumeric_-space "Notebook name")]}]
           (style/create-validation-error-message validation-errors)
           [comps/ErrorViewer {:error server-error}]])}]))

   :-duplicate-notebook
   (fn [{:keys [props state this refs]}]
     (let [{:keys [choose-notebook pet-token notebooks refresh-notebooks dismiss]} props
           bucket-name (get-in props [:workspace :workspace :bucketName])
           [new-notebook-name & fails] (input/get-and-validate refs "newNotebookName")]
       (if fails
         (swap! state assoc :validation-errors fails)
         (if (= (notebook-utils/notebook-name choose-notebook) new-notebook-name)
           (dismiss) ; no-op if the name is unchanged
           (if (some (comp (partial = new-notebook-name) notebook-utils/notebook-name) notebooks)
             (swap! state assoc :validation-errors [(str "Notebook with name \"" new-notebook-name "\" already exists")]) ; fail if a notebook already exists with the same name
             (do
               (swap! state assoc :duplicating? true)
               (notebook-utils/copy-notebook bucket-name pet-token choose-notebook new-notebook-name
                                             (fn [{:keys [success? raw-response]}]
                                               (swap! state assoc :duplicating? false)
                                               (if success?
                                                 (do
                                                   (refresh-notebooks)
                                                   (dismiss))
                                                 (swap! state assoc :server-response {:server-error (notebook-utils/parse-gcs-error raw-response)}))))))))))})
