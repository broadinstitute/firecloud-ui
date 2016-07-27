(ns org.broadinstitute.firecloud-ui.page.workspace.method-configs.publish
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    ))


(react/defc PublishDialog
  {:render
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id config]} props
           {:strs [namespace name]} config]
       [dialog/OKCancelForm
        {:header "Publish Method Configuration"
         :get-first-element-dom-node #(@refs "mcNamespace")
         :get-last-element-dom-node #(react/find-dom-node (@refs "publishButton"))
         :content
         (react/create-element
           [:div {:style {:width 500}}
            (when (:publishing? @state)
              [comps/Blocker {:banner "Publishing Method Configuration..."}])
            (style/create-form-label "Method Configuration Namespace")
            (style/create-text-field {:style {:width "100%"} :ref "mcNamespace"
                                      :defaultValue namespace})
            (style/create-form-label "Method Configuration Name")
            (style/create-text-field {:style {:width "100%"} :ref "mcName"
                                      :defaultValue name})
            (when (:error @state)
              [comps/ErrorViewer {:error (:error @state)}])])
         :dismiss-self modal/pop-modal
         :ok-button
         (react/create-element
           [comps/Button {:text "Publish" :ref "publishButton"
                          :onClick
                          #(let [[ns n] (common/get-text refs "mcNamespace" "mcName")]
                            (swap! state assoc :publishing? true :error nil)
                            (endpoints/call-ajax-orch
                              {:endpoint (endpoints/copy-method-config-to-repo workspace-id config)
                               :headers {"Content-Type" "application/json"}
                               :payload  {:configurationNamespace ns,
                                          :configurationName n,
                                          :sourceNamespace namespace,
                                          :sourceName name}
                               :on-done  (fn [{:keys [success? get-parsed-response]}]
                                           (swap! state dissoc :publishing?)
                                           (if success?
                                             (modal/pop-modal)
                                             (swap! state assoc :error (get-parsed-response))))}))}])}]))})
