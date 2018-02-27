(ns broadfcui.components.workspace-selector
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.create :as ws-create]
   [broadfcui.utils :as utils]
   ))


(react/defc WorkspaceSelector
  {:validate
   (fn [{:keys [state locals]}]
     (let [{:keys [selected-index]} @state]
       (case selected-index
         0 ["No workspace selected"]
         1 ((:new-workspace-form @locals) :validate)
         nil)))
   :get-selected-workspace
   (fn [{:keys [state locals]}]
     (let [{:keys [workspaces selected-index]} @state]
       (case selected-index
         0 {:error "No workspace selected"}
         1 {:new-workspace ((:new-workspace-form @locals) :get-field-values)}
         {:existing-workspace (nth workspaces (- selected-index 2))})))
   :get-default-props
   (fn []
     {:filter identity
      :sort-by (comp (partial mapv string/lower-case)
                     (juxt :namespace :name)
                     :workspace)})
   :get-initial-state
   (fn []
     {:selected-index 0})
   :render
   (fn [{:keys [props state locals]}]
     (let [{:keys [workspaces selected-index]} @state
           {:keys [style]} props]
       (if-not workspaces
         (spinner "Loading workspaces...")
         [:div {:data-test-id "workspace-selector"}
          (style/create-select
            {:data-test-id "destination-workspace"
             :defaultValue ""
             :ref (fn [elem]
                    ;; doing this manually because the ref handler thingy calls :did-mount overzealously
                    (when (and elem (not (:select @locals)))
                      (swap! locals assoc :select elem)
                      (.on (.select2 (js/$ elem)) "select2:select"
                           (fn [event]
                             (swap! state assoc :selected-index (-> event .-target .-value int))))))
             :style (merge {:width 500} style)}
            (->> workspaces
                 (map (comp common/workspace-id->string :workspace))
                 (concat ["Select a workspace" "Create new workspace..."])))
          ;; Doing this via display: none to maintain state when the component is hidden
          [:div {:style {:display (when-not (= 1 selected-index) "none")
                         :border style/standard-line
                         :padding "0.5rem" :paddingBottom 0
                         :margin "-0.5rem" :marginTop "0.5rem"}}
           [ws-create/WorkspaceCreationForm {:ref #(swap! locals assoc :new-workspace-form %)}]]])))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint endpoints/list-workspaces
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (swap! state assoc :workspaces (->> (get-parsed-response)
                                                        (filter (:filter props))
                                                        (sort-by (:sort-by props))))
                    (swap! state assoc :error status-text)))}))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.select2 (js/$ (:select @locals)) "destroy"))})
