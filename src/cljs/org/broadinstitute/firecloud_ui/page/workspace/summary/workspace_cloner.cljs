(ns org.broadinstitute.firecloud-ui.page.workspace.summary.workspace-cloner
  (:require
    [clojure.string :refer [blank?]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    ))

(react/defc WorkspaceCloner
  {:render
   (fn [{:keys [props refs state this]}]
     [comps/Dialog {:width 400 :dismiss-self (:dismiss props)
                    :content
                    (react/create-element
                      [comps/OKCancelForm
                       {:header "Clone Workspace to:"
                        :dismiss-self (:dismiss props)
                        :content
                        (react/create-element
                          [:div {}
                           (when (:working? @state)
                             [comps/Blocker {:banner "Cloning..."}])
                           (style/create-form-label "Google Project")
                           (style/create-text-field {:ref "namespace"
                                                     :style {:width "100%"}
                                                     :defaultValue (get-in props [:workspace-id :namespace])
                                                     :placeholder "Required"
                                                     :onChange #(swap! state dissoc :error)})
                           (style/create-form-label "Name")
                           (style/create-text-field {:ref "name"
                                                     :style {:width "100%"}
                                                     :defaultValue (get-in props [:workspace-id :name])
                                                     :placeholder "Required"
                                                     :onChange #(swap! state dissoc :error)})
                           (when (:error @state)
                             [:div {:style {:color (:exception-red style/colors)}}
                              (:error @state)])])
                        :ok-button
                        (react/create-element
                          [comps/Button {:text "OK" :ref "okButton"
                                         :onClick #(react/call :do-clone this)}])}])
                    :get-first-element-dom-node #(.getDOMNode (@refs "namespace"))
                    :get-last-element-dom-node #(.getDOMNode (@refs "okButton"))}])
   :component-did-mount
   (fn []
     (common/scroll-to-top 100))
   :do-clone
   (fn [{:keys [props refs state]}]
     (let [[namespace name] (common/get-text refs "namespace" "name")]
       (if (some blank? [namespace name])
         (swap! state assoc :error "All fields required")
         (do
           (swap! state assoc :working? true)
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/clone-workspace (:workspace-id props))
              :payload {:namespace namespace :name name}
              :headers {"Content-Type" "application/json"}
              :on-done (fn [{:keys [success? status-text]}]
                         (swap! state dissoc :working?)
                         (if success?
                           ((:dismiss props))
                           (swap! state assoc :error status-text)))})))))})