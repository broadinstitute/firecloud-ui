(ns org.broadinstitute.firecloud-ui.page.workspace.summary.attribute-editor
  (:require
    clojure.set
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc WorkspaceAttributeViewerEditor
  {:get-attributes
   (fn [{:keys [state]}]
     (into {} (:attributes @state)))
   :render
   (fn [{:keys [props state refs]}]
     (let [{:keys [editing?]} props]
       [:div {}
        (style/create-section-header "Workspace Attributes")
        (style/create-paragraph
          [:div {}
           (when editing?
             [:div {:style {:marginBottom "0.25em"}}
              [comps/Button {:icon :add :text "Add new"
                             :onClick (fn [_]
                                        (swap! state update :attributes conj ["" ""])
                                        (js/setTimeout
                                          #(common/focus-and-select
                                            (->> @state :attributes count dec (str "key_") (@refs)))
                                          0))}]])
           [table/Table
            {:key editing?
             :reorderable-columns? false :sortable-columns? (not editing?) :filterable? false :pagination :none
             :empty-message "No Workspace Attributes defined"
             :columns (if editing?
                        [{:starting-width 40 :resizable? false :as-text (constantly "Delete")
                          :content-renderer
                          (fn [index]
                            (icons/icon {:style {:color (:exception-red style/colors)
                                                 :verticalAlign "middle"
                                                 :cursor "pointer"}
                                         :onClick #(swap! state update :attributes utils/delete index)}
                                        :delete))}
                         {:header "Key" :starting-width 200 :as-text (constantly nil)
                          :content-renderer
                          (fn [{:keys [key index]}]
                            (style/create-text-field {:ref (str "key_" index) :key index
                                                      :style {:marginBottom 0 :width "calc(100% - 2px)"}
                                                      :value (name key)
                                                      :onChange #(swap! state update-in [:attributes index]
                                                                        assoc 0 (keyword (-> % .-target .-value)))}))}
                         {:header "Value" :starting-width 600 :as-text (constantly nil)
                          :content-renderer
                          (fn [{:keys [value index]}]
                            (style/create-text-field {:key index
                                                      :style {:marginBottom 0 :width "calc(100% - 2px)"}
                                                      :value value
                                                      :onChange #(swap! state update-in [:attributes index]
                                                                        assoc 1 (-> % .-target .-value))}))}]
                        [{:header "Key" :starting-width 200 :as-text name}
                         {:header "Value" :starting-width 600}])
             :data (if editing?
                     (map-indexed (fn [index [key value]]
                                    {:index index :key key :value value})
                                  (:attributes @state))
                     (:workspace-attributes props))
             :->row (if editing?
                      (juxt :index identity identity)
                      identity)}]])]))
   :component-did-update
   (fn [{:keys [prev-props props state]}]
     (when (and (not (:editing? prev-props)) (:editing? props))
       (swap! state assoc :attributes (into [] (:workspace-attributes props)))))})
