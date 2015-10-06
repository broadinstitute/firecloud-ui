(ns org.broadinstitute.firecloud-ui.page.workspace.details
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.page.workspace.data-tab :as data-tab]
    [org.broadinstitute.firecloud-ui.page.workspace.method-configs-tab :as method-configs-tab]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor-tab :as monitor-tab]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.summary-tab :as summary-tab]
    ))


(react/defc WorkspaceDetails
  {:render
   (fn [{:keys [props refs]}]
     [:div {}
      [comps/TabBar {:ref "tab-bar"
                     :items
                     [{:text "Summary"
                       :render #(summary-tab/render (:workspace-id props) (:on-delete props))}
                      {:text "Data" :render #(data-tab/render (:workspace-id props))}
                      {:text "Method Configurations"
                       :render (fn []
                                 (method-configs-tab/render
                                  (:workspace-id props)
                                  #(react/call :set-active-tab (@refs "tab-bar") 3 %)))}
                      {:text "Monitor" :render #(monitor-tab/render (:workspace-id props) %)}]}]])})


(defn render-workspace-details [workspace-id on-delete]
  (react/create-element WorkspaceDetails {:workspace-id workspace-id :on-delete on-delete}))
