(ns org.broadinstitute.firecloud-ui.page.workspace.details
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.page.workspace.workspace-summary :refer [render-workspace-summary]]
    [org.broadinstitute.firecloud-ui.page.workspace.workspace-data :refer [render-workspace-data]]
    [org.broadinstitute.firecloud-ui.page.workspace.method-configs :refer [render-method-configs]]
    [org.broadinstitute.firecloud-ui.paths :as paths]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc WorkspaceDetails
  {:render
   (fn [{:keys [state]}]
     [:div {}
      (cond
        (nil? (:server-response @state))
        [comps/Spinner {:text "Loading workspace..."}]
        (get-in @state [:server-response :error-message])
        [:div {:style {:textAlign "center" :color (:exception-red style/colors)}}
         (get-in @state [:server-response :error-message])]
        :else
        (let [ws (get-in @state [:server-response :workspace])]
          [comps/TabBar {:key "selected"
                         :items [{:text "Summary" :component (render-workspace-summary ws)}
                                 {:text "Data" :component (render-workspace-data ws)}
                                 {:text "Method Configurations"
                                  :component (render-method-configs ws)}
                                 {:text "Monitor"}
                                 {:text "Files"}]}]))])
   :load-workspace
   (fn [{:keys [props state]}]
     (utils/call-ajax-orch
       (paths/workspace-details-path (:workspace-id props))
       {:on-success (fn [{:keys [parsed-response]}]
                      (swap! state assoc :server-response {:workspace (merge {"status" "Complete"} ;; TODO Remove.
                                                                        parsed-response)}))
        :on-failure (fn [{:keys [status-text]}]
                      (swap! state assoc :server-response {:error-message status-text}))
        :mock-data (merge (:workspace-id props) {:status "Complete" :createdBy "Nobody"})}))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-workspace this))
   :component-did-update
   (fn [{:keys [this state]}]
     (when-not (:server-response @state)
       (react/call :load-workspace this)))
   :component-will-receive-props
   (fn [{:keys [props next-props state]}]
     (when-not (apply = (map :workspace-id [props next-props]))
       (swap! state assoc :server-response nil)))})


(defn render-workspace-details [workspace-id]
  (react/create-element WorkspaceDetails {:workspace-id workspace-id}))
