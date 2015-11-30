(ns org.broadinstitute.firecloud-ui.page.workspace.details
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.page.workspace.data.tab :as data-tab]
    [org.broadinstitute.firecloud-ui.page.workspace.method-configs.tab :as method-configs-tab]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.tab :as monitor-tab]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.tab :as summary-tab]
    ))


(def ^:private SUMMARY "Summary")
(def ^:private DATA "Data")
(def ^:private CONFIGS "Method Configurations")
(def ^:private MONITOR "Monitor")
(defn- tab-string-to-index [tab-string]
  ;; for some reason the more compact "case" isn't working with strings :(
  (cond
    (= tab-string SUMMARY) 0
    (= tab-string DATA) 1
    (= tab-string CONFIGS) 2
    (= tab-string MONITOR) 3
    :else 0))

(react/defc WorkspaceDetails
  {:render
   (fn [{:keys [props refs]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           workspace-id (common/get-id-from-nav-segment (:segment nav-context))
           tab-context (nav/parse-segment nav-context)
           tab (:segment tab-context)]
       [:div {:style {:margin "0 -1em"}}
        [comps/TabBar {:ref "tab-bar"
                       :initial-tab-index (tab-string-to-index tab)
                       :items
                       [{:text SUMMARY
                         :render #(summary-tab/render
                                    workspace-id
                                    (:on-delete props)
                                    nav-context)
                         :onTabSelected #(nav/navigate nav-context SUMMARY)}
                        {:text DATA
                         :render #(data-tab/render (:workspace-id props))
                         :onTabSelected #(nav/navigate nav-context DATA)}
                        {:text CONFIGS
                         :render (fn []
                                   (method-configs-tab/render
                                     workspace-id
                                     #(react/call :set-active-tab (@refs "tab-bar") 3 %)
                                     tab-context))
                         :onTabSelected #(when (empty? (:remaining tab-context))
                                           (nav/navigate nav-context CONFIGS))}
                        {:text MONITOR
                         :render (fn [submission-id]
                                   (monitor-tab/render workspace-id submission-id))
                         :onTabSelected #(nav/navigate nav-context MONITOR)}]}]]))})


(defn render-workspace-details [workspace-id on-delete nav-context]
  (react/create-element WorkspaceDetails {:nav-context nav-context
                                          :workspace-id workspace-id
                                          :on-delete on-delete}))
