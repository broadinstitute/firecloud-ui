(ns broadfcui.page.workspace.notebooks.create-notebook
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

(def base-notebook
  {:cells [{:cell_type "code"
            :execution_count nil
            :metadata {}
            :outputs []
            :source []}]
   :nbformat 4
   :nbformat_minor 2}
  )

(def python2-notebook
  (merge base-notebook {:metadata
                        {:kernelspec {:display_name "Python 2"
                                      :language "python",
                                      :name "python2"}}}))

(def python3-notebook
  (merge base-notebook {:metadata
                        {:kernelspec {:display_name "Python 3"
                                      :language "python",
                                      :name "python3"}}}))

(def hail01-notebook
  (merge base-notebook {:metadata
                        {:kernelspec {:display_name "PySpark 2"
                                      :language "python",
                                      :name "pyspark2"}}}))

(def hail02-notebook
  (merge base-notebook {:metadata
                        {:kernelspec {:display_name "PySpark 3"
                                      :language "python",
                                      :name "pyspark3"}}}))

(def r-notebook
  (merge base-notebook {:metadata
                        {:kernelspec {:display_name "R"
                                      :language "R"
                                      :name "ir"}
                         :language_info {:codemirror_mode "r"
                                         :file_extension ".r"
                                         :mimetype "text/x-r-source"
                                         :name "R"
                                         :pygments_lexer "r"
                                         :version "3.3.3"}}}))

(def kernel-map {"Python 2" python2-notebook
                 "Python 3" python3-notebook
                 "R" r-notebook
                 "Hail 0.1" hail01-notebook
                 "Hail 0.2" hail02-notebook})

(react/defc NotebookCreator
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [creating? server-response validation-errors]} @state
           {:keys [server-error]} server-response
           {:keys [dismiss]} props]
       [modals/OKCancelForm
        {:header "Create New Notebook"
         :dismiss dismiss
         :ok-button {:text "Create" :onClick #(this :-create-notebook)}
         :content
         (react/create-element
          [:div {:style {:marginTop 0}}
           (when creating? (blocker "Creating notebook..."))
           [comps/ErrorViewer {:error server-error}]

           [:div {:style {:width "48%" :marginRight "4%" :marginBottom "1%"}}
            [FoundationTooltip {:text (notebook-utils/create-inline-form-label "Name")
                                :tooltip "The name of the notebook. Does not need to include .ipynb. This can be changed later."}]]
           [input/TextField {:data-test-id "notebook-name-input" :ref "newNotebookName" :autoFocus true :style {:width "100%"}
                             :defaultValue "" :predicates [(input/nonempty "Notebook name") (input/alphanumeric_-space "Notebook name")]}]
           [:div {:style {:width "48%" :marginRight "4%" :marginBottom "1%"}}
            [FoundationTooltip {:text (notebook-utils/create-inline-form-label "Kernel")
                                :tooltip "The notebook kernel. This can be changed later."}]]
           (style/create-identity-select {:data-test-id "kernel-select" :ref "newNotebookKernel"
                                          :style {:width "100%"} :defaultValue "Python 3"}
             (keys kernel-map))
           (style/create-validation-error-message validation-errors)])}]))

   :-create-notebook
   (fn [{:keys [props state this refs]}]
     (let [{:keys [pet-token notebooks refresh-notebooks dismiss]} props
           bucket-name (get-in props [:workspace :workspace :bucketName])
           [new-notebook-name & fails] (input/get-and-validate refs "newNotebookName")
           new-notebook-kernel (.-value (@refs "newNotebookKernel"))]
       (if fails
         (swap! state assoc :validation-errors fails)
         ; fail if a notebook already exists with the same name
         (if (some (comp (partial = new-notebook-name) notebook-utils/notebook-name) notebooks)
           (swap! state assoc :validation-errors [(str "Notebook with name \"" new-notebook-name "\" already exists")])
           (do
             (swap! state assoc :creating? true)
             (notebook-utils/create-notebook bucket-name pet-token new-notebook-name (get kernel-map new-notebook-kernel)
                                             (fn [{:keys [success? raw-response]}]
                                               (swap! state assoc :creating? false)
                                               (if success?
                                                 (do
                                                   (refresh-notebooks)
                                                   (dismiss))
                                                 (swap! state assoc :server-response {:server-error raw-response})))))))))})
