(ns org.broadinstitute.firecloud-ui.page.workspace.summary.attribute-editor
  (:require
    clojure.set
    [clojure.string :refer [join trim]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc WorkspaceAttributeViewerEditor
  {:get-attributes
   (fn [{:keys [state]}]
     (let [{:keys [attributes]} @state
           duplicates (not-empty (utils/find-duplicates (map key attributes)))
           any-empty? (some (fn [[k v]]
                              (let [[ek ev] (map (comp empty? trim) [k v])]
                                (or ek ev)))
                            attributes)
           with-spaces (->> attributes
                            (map (comp trim key))
                            (filter (partial re-find #"\s"))
                            not-empty)]
       (cond duplicates {:error (str "Duplicate keys: " (join ", " duplicates))}
             any-empty? {:error "Empty keys and values are not allowed."}
             with-spaces {:error (str "Keys cannot have spaces: " (join ", " with-spaces))}
             :else {:success (into {} attributes)})))
   :render
   (fn [{:keys [props state after-update]}]
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
                                        ;; have to do this by ID not ref, since the fields are generated within Table
                                        (after-update #(.focus (.getElementById js/document "focus"))))}]])
           [table/Table
            {:key editing?
             :reorderable-columns? false :sortable-columns? (not editing?) :filterable? false :pagination :none
             :empty-message "No Workspace Attributes defined"
             :row-style {:borderBottom (str "2px solid " (:line-gray style/colors))}
             :header-row-style {:borderBottom (str "2px solid " (:line-gray style/colors))
                                :color (:text-gray style/colors) :backgroundColor "white"}
             :resize-tab-color (:line-gray style/colors)
             :columns (if editing?
                        [{:starting-width 40 :resizable? false :as-text (constantly "Delete")
                          :content-renderer
                          (fn [index]
                            (icons/icon {:style {:color (:exception-red style/colors)
                                                 :verticalAlign "middle"
                                                 :cursor "pointer"}
                                         :onClick #(swap! state update :attributes utils/delete index)}
                                        :delete))}
                         {:header "Key" :starting-width 300 :as-text (constantly nil)
                          :content-renderer
                          (fn [{:keys [key index]}]
                            (style/create-text-field {:key index
                                                      :id (when (= index (-> (:attributes @state) count dec)) "focus")
                                                      :style {:marginBottom 0 :width "calc(100% - 2px)"}
                                                      :value key
                                                      :onChange #(swap! state update-in [:attributes index]
                                                                        assoc 0 (-> % .-target .-value))}))}
                         {:header "Value" :starting-width 600 :as-text (constantly nil)
                          :content-renderer
                          (fn [{:keys [value index]}]
                            (style/create-text-field {:key index
                                                      :style {:marginBottom 0 :width "calc(100% - 2px)"}
                                                      :value value
                                                      :onChange #(swap! state update-in [:attributes index]
                                                                        assoc 1 (-> % .-target .-value))}))}]
                        [{:header "Key" :starting-width 300 :as-text name
                          :content-renderer (fn [key] [:b {} (name key)])}
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
       (swap! state assoc :attributes (mapv (fn [[k v]] [(name k) v]) (:workspace-attributes props)))))})
