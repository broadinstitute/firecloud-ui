(ns broadfcui.page.workspace.notebooks.create_notebook
  (:require
   [dmohs.react :as react]
   [broadfcui.components.modals :as modals]
   [broadfcui.common.style :as style]
   [broadfcui.common.input :as input]
   [broadfcui.page.workspace.notebooks.utils :as utils]
   ))

(def kernels ["Python 2"
              "Python 3"
              "R"
              "Hail 0.1"
              "Hail 0.2"])

(react/defc NotebookCreator
  {:render
   (fn [{:keys [props state this]}]
     [modals/OKCancelForm
      {:header "Create New Notebook"
       :dismiss (:dismiss props)
       :ok-button {:text "Create"
                   :onClick #(this :-create-notebook)}
       :content
       (react/create-element
        [:div {:style {:marginTop 0}}
         (style/create-form-label "Name")
         [input/TextField {:data-test-id "notebook-name-input" :ref "notebookNameCreate" :autoFocus true :style {:width "100%"}
                           :defaultValue "" :predicates [(input/nonempty "Notebook name") (input/alphanumeric_- "Notebook name")]}]

         (style/create-form-label "Kernel")
         (style/create-identity-select {:data-test-id "kernel-select" :ref "kernel"
                                        :style {:width "100%"} :defaultValue "Python 3"}
                                       kernels)])}])

   :-create-notebook
   (fn [{:keys [props state this]}]
     (do
       ((:dismiss props))
       ; ((:reload-after-create props))
       ))})


