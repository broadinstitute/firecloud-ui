(ns broadfcui.page.method-repo.method.export-destination-form
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.input :as input]
   [broadfcui.common.style :as style]
   [broadfcui.components.workspace-selector :refer [WorkspaceSelector]]
   [broadfcui.utils :as utils]
   ))


(react/defc ExportDestinationForm
  {:validate
   (fn [{:keys [props state refs locals]}]
     (let [{:keys [workspace-id]} props
           {:keys [workspace-selector]} @locals
           validation-errors (->> (input/validate refs "name-field")
                                  (concat (when-not workspace-id (workspace-selector :validate)))
                                  not-empty)]
       (swap! state assoc :validation-errors validation-errors)
       validation-errors))
   :get-field-values
   (fn [{:keys [props refs locals]}]
     (let [{:keys [workspace-id select-root-entity-type?]} props
           {:keys [workspace-selector]} @locals]
       (merge {:name (input/get-text refs "name-field")}
              (when select-root-entity-type?
                {:root-entity-type (.-value (@refs "root-entity-type"))})
              (when-not workspace-id
                {:workspace (workspace-selector :get-selected-workspace)}))))
   :render
   (fn [{:keys [props state locals]}]
     (let [{:keys [initial-name workspace-id select-root-entity-type?]} props]
       [:div {:style {:width 550}}
        (style/create-form-label "Name")
        [input/TextField {:ref "name-field"
                          :style {:width "100%"}
                          :defaultValue initial-name
                          :predicates [(input/nonempty-alphanumeric_-period "Name")]}]
        (when select-root-entity-type?
          (list
           (style/create-form-label "Root Entity Type")
           (style/create-identity-select {:ref "root-entity-type"}
             common/root-entity-types)))
        (when-not workspace-id
          (list
           (style/create-form-label "Destination Workspace")
           [WorkspaceSelector {:ref #(swap! locals assoc :workspace-selector %)
                               :style {:width "100%"}
                               :filter #(common/access-greater-than-equal-to? (:accessLevel %) "WRITER")}]))
        [:div {:style {:padding "0.5rem"}}] ;; select2 is eating any padding/margin I give to WorkspaceSelector
        (style/create-validation-error-message (:validation-errors @state))]))})
