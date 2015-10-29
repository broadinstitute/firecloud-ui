(ns org.broadinstitute.firecloud-ui.page.workspace.method-configs.publish
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    ))


(defn- render-modal-publish [state refs props]
  (react/create-element
    [dialog/OKCancelForm
     {:header "Publish Method Configuration"
      :content
      (react/create-element
        [:div {}
         (when (:publishing? @state)
           [comps/Blocker {:banner "Publishing Method Configuration..."}])
         (style/create-form-label "Method Configuration Namespace")
         (style/create-text-field {:style {:width "100%"} :ref "mcNamespace"
                                   :defaultValue (get-in (:config props) ["namespace"])})
         (style/create-form-label "Method Configuration Name")
         (style/create-text-field {:style {:width "100%"} :ref "mcName"
                                   :defaultValue (get-in (:config props) ["name"])})
         (when (:error @state)
           [comps/ErrorViewer {:error (:error @state)}])])
      :dismiss-self (:dismiss-self props)
      :ok-button
      (react/create-element
        [comps/Button {:text "Publish" :ref "publishButton"
                       :onClick
                       #(let [[ns n] (common/get-text refs "mcNamespace" "mcName")]
                         (swap! state assoc :publishing? true :error nil)
                         (endpoints/call-ajax-orch
                           {:endpoint (endpoints/copy-method-config-to-repo (:workspace-id props) (:config props))
                            :headers {"Content-Type" "application/json"}
                            :payload  {:configurationNamespace ns,
                                       :configurationName n,
                                       :sourceNamespace (get-in (:config props) ["namespace"]),
                                       :sourceName (get-in (:config props) ["name"])}
                            :on-done  (fn [{:keys [success? get-parsed-response]}]
                                        (swap! state dissoc :publishing?)
                                        (if success?
                                          ((:dismiss-self props))
                                          (swap! state assoc :error (get-parsed-response))))}))}])}]))

(react/defc PublishDialog
  {:render
   (fn [{:keys [props state refs]}]
     [dialog/Dialog
      {:width 500
       :dismiss-self (:dismiss-self props)
       :content (render-modal-publish state refs props)
       :get-first-element-dom-node #(.getDOMNode (@refs "mcNamespace"))
       :get-last-element-dom-node #(.getDOMNode (@refs "publishButton"))}])})
