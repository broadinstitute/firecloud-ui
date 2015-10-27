(ns org.broadinstitute.firecloud-ui.page.workspace.method-configs.tab
  (:require
    [dmohs.react :as react]
    clojure.string
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.method-config-importer :refer [MethodConfigImporter]]
    [org.broadinstitute.firecloud-ui.page.workspace.method-configs.method-config-editor :refer [MethodConfigEditor]]
    ))


(react/defc MethodConfigurationsList
  {:render
   (fn [{:keys [props state]}]
     [:div {}
      (when (:show-import-overlay? @state)
        [comps/Dialog
         {:blocker? true
          :width "80%"
          :dismiss-self #(swap! state dissoc :show-import-overlay?)
          :content (react/create-element
                     [:div {:style {:padding "1em"}}
                      [comps/XButton {:dismiss #(swap! state dissoc :show-import-overlay?)}]
                      [MethodConfigImporter {:workspace-id (:workspace-id props)
                                             :after-import (fn [config]
                                                             (swap! state dissoc :show-import-overlay?)
                                                             ((:on-config-imported props) config))}]])}])
      (let [server-response (:server-response @state)
            {:keys [configs error-message]} server-response]
        (cond
          (or (nil? server-response) (not (contains? @state :locked?)))
          [comps/Spinner {:text "Loading configurations..."}]
          error-message (style/create-server-error-message error-message)
          :else
          [table/Table
           {:empty-message "There are no method configurations to display."
            :toolbar (fn [built-in]
                       [:div {}
                        [:div {:style {:float "left" :margin "5 0 -5 0"}} built-in]
                        [:div {:style {:float "right" :paddingRight "2em"}}
                         [comps/Button {:text "Import Configuration..." :disabled? (when (:locked? @state) "The workspace is locked")
                                        :onClick #(swap! state assoc :show-import-overlay? true)}]]
                        (common/clear-both)])
            :columns
            [{:header "Name" :starting-width 240 :as-text #(% "name") :sort-by :text
              :content-renderer
              (fn [config]
                (style/create-link
                  #((:on-config-selected props) config)
                  (config "name")))}
             {:header "Root Entity Type" :starting-width 140}
             {:header "Method" :starting-width 800
              :content-renderer (fn [fields] (apply style/render-entity fields))}]
            :data configs
            :->row (fn [config]
                     [config
                      (config "rootEntityType")
                      (mapv #(get-in config ["methodRepoMethod" %])
                            ["methodNamespace" "methodName" "methodVersion"])])}]))])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load this))
   :component-did-update
   (fn [{:keys [this state]}]
     (when (nil? (:server-response @state))
       (react/call :load this)))
   :load
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :locked? (get-in (get-parsed-response) ["workspace" "isLocked"]))
                     (swap! state assoc :error status-text)))})
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/list-workspace-method-configs (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :server-response {:configs (vec (get-parsed-response))})
                     (swap! state assoc :server-response {:error-message status-text})))}))})


(react/defc Page
  {:render
   (fn [{:keys [props state]}]
     [:div {:style {:padding "1em 0"}}
      (if (:selected-method-config @state)
        [MethodConfigEditor {:config (:selected-method-config @state)
                             :workspace-id (:workspace-id props)
                             :on-submission-success (:on-submission-success props)
                             :on-rm (fn [] (swap! state dissoc :selected-method-config))}]
        [MethodConfigurationsList
         {:workspace-id (:workspace-id props)
          ;TODO: For both callbacks - rename config to config-id and follow the workspace-id pattern
          :on-config-selected (fn [config]
                                (swap! state assoc :selected-method-config config))
          :on-config-imported (fn [config]
                                (swap! state assoc :selected-method-config config))}])])
   :component-will-receive-props
   (fn [{:keys [state]}]
     (swap! state dissoc :selected-method-config))})


(defn render [workspace-id on-submission-success]
  [Page {:workspace-id workspace-id :on-submission-success on-submission-success}])
