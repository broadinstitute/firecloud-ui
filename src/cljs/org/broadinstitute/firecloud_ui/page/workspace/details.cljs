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
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(def ^:private SUMMARY "Summary")
(def ^:private DATA "Data")
(def ^:private CONFIGS "Method Configurations")
(def ^:private MONITOR "Monitor")
(defn- tab-string-to-index [tab-string]
  ;; for some reason the more compact "case" isn't working with strings :(
  (cond
    (= tab-string DATA) 1
    (= tab-string CONFIGS) 2
    (= tab-string MONITOR) 3
    :else 0))

(react/defc WorkspaceDetails
  {:render
   (fn [{:keys [props refs]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           workspace-id (:workspace-id props)
           tab (:segment nav-context)]
       [:div {:style {:margin "0 -1em"}}
        [comps/TabBar {:selected-index (tab-string-to-index tab)
                       :items
                       [{:text SUMMARY :href (nav/create-href (:nav-context props))
                         :content
                         (react/create-element
                           [summary-tab/Summary {:key workspace-id :ref SUMMARY
                                                 :workspace-id workspace-id
                                                 :nav-context nav-context
                                                 :on-delete (:on-delete props)
                                                 :on-clone (:on-clone props)}])
                         :onTabRefreshed #(react/call :refresh (@refs SUMMARY))}
                        {:text DATA :href (nav/create-href (:nav-context props) DATA)
                         :content
                         (react/create-element
                           [data-tab/WorkspaceData {:ref DATA :workspace-id workspace-id}])
                         :onTabRefreshed #(react/call :refresh (@refs DATA))}
                        {:text CONFIGS :href (nav/create-href (:nav-context props) CONFIGS)
                         :content
                         (react/create-element
                           [method-configs-tab/Page {:ref CONFIGS
                                                     :workspace-id workspace-id
                                                     :on-submission-success #(nav/navigate (:nav-context props) MONITOR %)
                                                     :nav-context (nav/terminate-when (not= tab CONFIGS) nav-context)}])
                         :onTabRefreshed #(react/call :refresh (@refs CONFIGS))}
                        {:text MONITOR :href (nav/create-href (:nav-context props) MONITOR)
                         :content
                         (react/create-element
                           [monitor-tab/Page {:ref MONITOR
                                              :workspace-id workspace-id
                                              :nav-context (nav/terminate-when (not= tab MONITOR) nav-context)}])
                         :onTabRefreshed #(react/call :refresh (@refs MONITOR))}]}]]))})
