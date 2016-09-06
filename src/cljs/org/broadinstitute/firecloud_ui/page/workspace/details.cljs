(ns org.broadinstitute.firecloud-ui.page.workspace.details
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
   [org.broadinstitute.firecloud-ui.nav :as nav]
   [org.broadinstitute.firecloud-ui.page.workspace.analysis.tab :as analysis-tab]
   [org.broadinstitute.firecloud-ui.page.workspace.data.tab :as data-tab]
   [org.broadinstitute.firecloud-ui.page.workspace.method-configs.tab :as method-configs-tab]
   [org.broadinstitute.firecloud-ui.page.workspace.monitor.tab :as monitor-tab]
   [org.broadinstitute.firecloud-ui.page.workspace.summary.tab :as summary-tab]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(react/defc ProtectedBanner
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [workspace workspace-error]} props]
       [:div {:style {:position "relative"}}
        (cond workspace-error
              [:div {:style {:color (:exception-red style/colors)}} workspace-error]
              workspace
              (when (get-in workspace ["workspace" "realm"])
                [:div {:style {}}
                 [:div {:style {:height 1 :backgroundColor "#bbb" :marginBottom 2}}]
                 [:div {:style {:outlineTop (str "4px double #ccc")
                                :backgroundColor "#ccc"
                                :fontSize "small"
                                :padding "4px 0"
                                :textAlign "center"}}
                  "This is a " [:b {} "restricted"] " workspace for TCGA Controlled Access Data."]])
              :else
              [:div {:style {:position "absolute" :marginTop "-1.5em"}}
               [comps/Spinner {:height "1.5ex"}]])]))})

(react/defc BucketBanner
  {:render
   (fn [{:keys [props]}]
    [:div {:style {:position "relative"}}
     (case (:bucket-access? props)
       nil [:div {:style {:position "absolute" :marginTop "-1.5em"}}
            [comps/Spinner {:height "1.5ex"}]]
       true nil
       false [:div {:style {}}
             [:div {:style {:height 1 :backgroundColor "#bbb" :marginBottom 2}}]
             [:div {:style {:outlineTop (str "4px double #ccc")
                            :backgroundColor "#efdcd7"
                            :fontSize "small"
                            :padding "4px 0"
                            :textAlign "center"}}
              (cond (= 404 (:bucket-status-code props))
                    (str "The Google bucket associated with this workspace"
                         " does not exist. Please contact help@firecloud.org.")
                    :else (str "The Google bucket associated with this workspace is currently unavailable."
                         " This should be resolved shortly. If this persists for more than an hour,"
                         " please contact help@firecloud.org."))]])])})


(def ^:private SUMMARY "Summary")
(def ^:private DATA "Data")
(def ^:private ANALYSIS "Analysis")
(def ^:private CONFIGS "Method Configurations")
(def ^:private MONITOR "Monitor")
(defn- tab-string-to-index [tab-string]
  ;; for some reason the more compact "case" isn't working with strings :(
  (cond
    (= tab-string DATA) 1
    (= tab-string ANALYSIS) 2
    (= tab-string CONFIGS) 3
    (= tab-string MONITOR) 4
    :else 0))

(react/defc WorkspaceDetails
  {:refresh-workspace
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/check-bucket-read-access (:workspace-id props))
        :on-done (fn [{:keys [success? status-code]}]
                   (swap! state assoc :bucket-status-code status-code :bucket-access? success?))})
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :workspace (get-parsed-response))
                     (swap! state assoc :workspace-error status-text)))}))
   :render
   (fn [{:keys [props state refs this]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           workspace-id (:workspace-id props)
           {:keys [workspace workspace-error bucket-access?]} @state
           tab (:segment nav-context)
           refresh #(react/call :refresh-workspace this)]
       [:div {:style {:margin "0 -1em"}}
        [:div {:style {:margin "-22px 0 2px 0"}}
         [ProtectedBanner (select-keys @state [:workspace :workspace-error])]]
        [:div {:style {:margin "-2px 0 2px 0"}}
         [BucketBanner (select-keys @state [:bucket-access? :bucket-status-code])]]
        [comps/TabBar {:selected-index (tab-string-to-index tab)
                       :items
                       [{:text SUMMARY :href (nav/create-href (:nav-context props))
                         :content
                         (react/create-element
                           [summary-tab/Summary {:key workspace-id :ref SUMMARY
                                                 :workspace-id workspace-id
                                                 :workspace workspace
                                                 :request-refresh refresh
                                                 :bucket-access? bucket-access?
                                                 :nav-context nav-context
                                                 :on-delete (:on-delete props)
                                                 :on-clone (:on-clone props)}])
                         :onTabRefreshed refresh}
                        {:text DATA :href (nav/create-href (:nav-context props) DATA)
                         :content
                         (react/create-element
                           [data-tab/WorkspaceData {:ref DATA
                                                    :workspace-id workspace-id
                                                    :workspace workspace
                                                    :workspace-error workspace-error
                                                    :request-refresh refresh}])
                         :onTabRefreshed refresh}
                        {:text ANALYSIS :href (nav/create-href (:nav-context props) ANALYSIS)
                         :content
                         (react/create-element
                           [analysis-tab/Page {:ref ANALYSIS :workspace-id workspace-id}])
                         :onTabRefreshed #(react/call :refresh (@refs ANALYSIS))}
                        {:text CONFIGS :href (nav/create-href (:nav-context props) CONFIGS)
                         :content
                         (react/create-element
                           [method-configs-tab/Page {:ref CONFIGS
                                                     :workspace-id workspace-id
                                                     :workspace workspace
                                                     :request-refresh refresh
                                                     :bucket-access? bucket-access?
                                                     :on-submission-success #(nav/navigate (:nav-context props) MONITOR %)
                                                     :nav-context (nav/terminate-when (not= tab CONFIGS) nav-context)}])
                         :onTabRefreshed #(react/call :refresh (@refs CONFIGS))}
                        {:text MONITOR :href (nav/create-href (:nav-context props) MONITOR)
                         :content
                         (react/create-element
                           [monitor-tab/Page {:ref MONITOR
                                              :workspace-id workspace-id
                                              :nav-context (nav/terminate-when (not= tab MONITOR) nav-context)}])
                         :onTabRefreshed #(react/call :refresh (@refs MONITOR))}]
                       :toolbar-right (when-let [analysis-tab (:analysis-tab @state)]
                                        (react/call :get-tracks-button analysis-tab))}]]))
   :component-did-mount
   (fn [{:keys [props this]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           tab (:segment nav-context)]
       ;; These tabs don't request a refresh, so if we nav straight there then we need to kick one off.
       (when (#{ANALYSIS CONFIGS MONITOR} tab)
         (react/call :refresh-workspace this))))
   :component-did-update
   (fn [{:keys [prev-state state refs]}]
     ;; when switching to the analysis tab, grab the ref to it so we can access the track picker button
     (when (not= (:analysis-tab prev-state) (@refs ANALYSIS))
       (swap! state assoc :analysis-tab (@refs ANALYSIS))))})
