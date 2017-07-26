(ns broadfcui.page.workspace.details
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.net :as net]
   [broadfcui.page.notifications :as notifications]
   [broadfcui.page.workspace.analysis.tab :as analysis-tab]
   [broadfcui.page.workspace.data.tab :as data-tab]
   [broadfcui.page.workspace.method-configs.tab :as method-configs-tab]
   [broadfcui.page.workspace.monitor.tab :as monitor-tab]
   [broadfcui.page.workspace.summary.tab :as summary-tab]
   [broadfcui.utils :as utils]
   ))


(defn- protected-banner [workspace]
  (let [this-auth-domain (get-in workspace [:workspace :authorizationDomain :membersGroupName])
        dbGapProtected (= this-auth-domain config/tcga-authorization-domain)]
    (when this-auth-domain
      [:div {:style {:paddingTop 2}}
       [:div {:style {:backgroundColor "#ccc"
                      :fontSize "small"
                      :padding "4px 0"
                      :textAlign "center"}
              :data-test-id "auth-domain-restriction-message"}
        "Access to this workspace is " [:strong {} "restricted"] " to: " this-auth-domain
        (when dbGapProtected " (TCGA Controlled Access Data)")]
       [:div {:style {:height 1 :backgroundColor "#bbb" :marginTop 2}}]])))

(defn- bucket-banner [{:keys [bucket-access? bucket-status-code]}]
  (when (= false bucket-access?)
    [:div {:style {:paddingTop 2}}
     [:div {:style {:backgroundColor "#efdcd7"
                    :fontSize "small"
                    :padding "4px 0"
                    :textAlign "center"}}
      (cond (= 404 bucket-status-code)
            (str "The Google bucket associated with this workspace"
                 " does not exist. Please contact help@firecloud.org.")
            :else (str "The Google bucket associated with this workspace is currently"
                       " unavailable. This should be resolved shortly. If this persists for"
                       " more than an hour, please contact help@firecloud.org."))]
     [:div {:style {:height 1 :backgroundColor "#efdcd7" :marginTop 2}}]]))


(def ^:private SUMMARY "Summary")
(def ^:private DATA "Data")
(def ^:private ANALYSIS "Analysis")
(def ^:private CONFIGS "Method Configurations")
(def ^:private MONITOR "Monitor")

(defn- process-workspace [raw-workspace]
  (let [attributes (get-in raw-workspace [:workspace :attributes])
        library-attributes (->> attributes
                                (filter (fn [[k _]]
                                          (.startsWith (name k) "library:")))
                                (into {}))
        tags (utils/filter-keys #(.startsWith (name %) "tag:") attributes)
        workspace-attributes (->> attributes
                                  (remove (fn [[k _]]
                                            (or (= k :description)
                                                (utils/contains (name k) ":"))))
                                  (into {}))]
    (-> raw-workspace
        (update :workspace dissoc :attributes)
        (assoc-in [:workspace :description] (:description attributes))
        (assoc-in [:workspace :workspace-attributes] workspace-attributes)
        (assoc-in [:workspace :tags] tags)
        (assoc-in [:workspace :library-attributes] library-attributes))))

(react/defc- Tab
  {:render
   (fn [{:keys [props state]}]
     [:a {:style {:flex "0 0 auto" :padding "1em 2em"
                  :borderLeft (when (:first? props) style/standard-line)
                  :borderRight style/standard-line
                  :backgroundColor (when (:active? props) "white")
                  :cursor "pointer" :textDecoration "none" :color "inherit"
                  :position "relative"}
          :href (:href props)
          :data-test-id (:data-test-id props)
          :onMouseOver #(swap! state assoc :hovering? true)
          :onMouseOut #(swap! state dissoc :hovering?)
          :onClick #(when (:active? props) ((:on-active-tab-clicked props)))}
      (:text props)
      (when (or (:active? props) (:hovering? @state))
        [:div {:style {:position "absolute" :top "-0.25rem" :left 0
                       :width "100%" :height "0.25rem"
                       :backgroundColor (:button-primary style/colors)}}])
      (when (:active? props)
        [:div {:style {:position "absolute" :bottom -1 :left 0 :width "100%" :height 2
                       :backgroundColor "white"}}])])})

(react/defc- WorkspaceDetails
  {:render
   (fn [{:keys [props state locals refs this]}]
     (let [{:keys [workspace-id]} props
           {:keys [workspace workspace-error bucket-access?]} @state
           active-tab (:tab-name props)
           is-active? (fn [tab] (if (= tab SUMMARY) (nil? active-tab) (= tab active-tab)))
           make-tab (fn [text on-active-tab-clicked]
                      [Tab {:text text :first? (= text SUMMARY) :active? (is-active? text)
                            :href (nav/get-link
                                   (condp = text
                                     SUMMARY :workspace-summary
                                     DATA :workspace-data
                                     ANALYSIS :workspace-analysis
                                     CONFIGS :workspace-method-configs
                                     MONITOR :workspace-monitor)
                                   workspace-id)
                            :on-active-tab-clicked on-active-tab-clicked
                            :data-test-id (str text "-tab")}])
           request-refresh #(this :-refresh-workspace)]
       [:div {}
        [:div {:style {:minHeight "0.5rem"}}
         (protected-banner workspace)
         (bucket-banner (select-keys @state [:bucket-access? :bucket-status-code]))]
        [:div {:style {:marginTop "1rem" :padding "0 1.5rem"
                       :display "flex" :justifyContent "space-between"}}
         [:div {:style {:fontSize "125%"}}
          "Workspace: "
          [:span {:style {:fontWeight 500}}
           [:span {:data-test-id "header-namespace"} (:namespace workspace-id)] "/" [:span {:data-test-id "header-name"} (:name workspace-id)]]]
         [common/FoundationTooltip
          {:tooltip "Adjust notifications for this workspace"
           :position "left"
           :style {:marginRight "-0.5rem" :borderBottom "none"}
           :data-hover-delay "1000" :data-click-open "false"
           :text
           (common/render-icon-dropdown
            {:icon-name :bell :icon-color (:text-light style/colors)
             :position "bottom"
             :button-class "float-right"
             :ref (fn [instance] (swap! locals assoc :infobox instance))
             :contents [notifications/WorkspaceComponent
                        (merge (select-keys props [:workspace-id])
                               {:close-self #((:infobox @locals) :close)})]})}]]
        [:div {:style {:marginTop "1rem"
                       :display "flex" :backgroundColor (:background-light style/colors)
                       :borderTop style/standard-line :borderBottom style/standard-line
                       :padding "0 1.5rem" :justifyContent "space-between"}}
         [:div {:style {:display "flex"}}
          (make-tab SUMMARY request-refresh)
          (make-tab DATA request-refresh)
          (make-tab ANALYSIS #((@refs ANALYSIS) :refresh))
          (make-tab CONFIGS #((@refs CONFIGS) :refresh))
          (make-tab MONITOR #((@refs MONITOR) :refresh))]]
        [:div {:style {:marginTop "2rem"}}
         (if-let [error (:workspace-error @state)]
           [:div {:style {:textAlign "center" :color (:exception-state style/colors)}
                  :data-test-id "workspace-details-error"}
            "Error loading workspace: " error]
           (condp = active-tab
             nil (react/create-element
                  [summary-tab/Summary
                   (merge {:key workspace-id :ref SUMMARY}
                          (utils/restructure workspace-id workspace request-refresh bucket-access?))])
             DATA (react/create-element
                   [data-tab/WorkspaceData
                    (merge {:ref DATA}
                           (utils/restructure workspace-id workspace workspace-error request-refresh))])
             ANALYSIS (react/create-element
                       [analysis-tab/Page {:ref ANALYSIS :workspace-id workspace-id}])
             CONFIGS (react/create-element
                      [method-configs-tab/Page
                       (merge {:ref CONFIGS
                               :on-submission-success #(nav/go-to-path
                                                        :workspace-submission workspace-id %)}
                              (utils/restructure workspace-id workspace request-refresh bucket-access?)
                              (select-keys props [:config-id]))])
             MONITOR (react/create-element
                      [monitor-tab/Page
                       (merge {:ref MONITOR}
                              (utils/restructure workspace-id workspace)
                              (select-keys props [:submission-id :workflow-id]))])))]]))
   :component-did-mount
   (fn [{:keys [props this]}]
     ;; These tabs don't request a refresh, so if we nav straight there then we need to kick one
     ;; off.
     (when (contains? #{ANALYSIS CONFIGS MONITOR} (:tab-name props))
       (this :-refresh-workspace)))
   :-refresh-workspace
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/check-bucket-read-access (:workspace-id props))
       :on-done (fn [{:keys [status-code success?]}]
                  (swap! state assoc :bucket-status-code status-code :bucket-access? success?))})
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-workspace (:workspace-id props))
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? parsed-response]}]
                   (if success?
                     (swap! state assoc :workspace (process-workspace parsed-response) :workspace-response parsed-response)
                     (swap! state assoc :workspace-error (:message parsed-response)))))}))})

(defn- ws-path [ws-id]
  (str "workspaces/" (:namespace ws-id) "/" (:name ws-id)))

(defn add-nav-paths []
  (nav/defpath
   :workspace-summary
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)"
    :make-props (fn [namespace name]
                  {:workspace-id (utils/restructure namespace name)})
    :make-path ws-path})
  (nav/defpath
   :workspace-data
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/data"
    :make-props (fn [namespace name]
                  {:workspace-id (utils/restructure namespace name) :tab-name "Data"})
    :make-path (fn [workspace-id]
                 (str (ws-path workspace-id) "/data"))})
  (nav/defpath
   :workspace-analysis
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/analysis"
    :make-props (fn [namespace name]
                  {:workspace-id (utils/restructure namespace name) :tab-name "Analysis"})
    :make-path (fn [workspace-id]
                 (str (ws-path workspace-id) "/analysis"))})
  (nav/defpath
   :workspace-method-configs
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/method-configs"
    :make-props (fn [namespace name]
                  {:workspace-id (utils/restructure namespace name)
                   :tab-name "Method Configurations"})
    :make-path (fn [workspace-id]
                 (str (ws-path workspace-id) "/method-configs"))})
  (nav/defpath
   :workspace-method-config
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/method-configs/([^/]+)/([^/]+)"
    :make-props (fn [namespace name config-ns config-name]
                  {:workspace-id (utils/restructure namespace name) :tab-name "Method Configurations"
                   :config-id {:namespace config-ns :name config-name}})
    :make-path (fn [workspace-id config-id]
                 (str (ws-path workspace-id) "/method-configs/"
                      (:namespace config-id) "/" (:name config-id)))})
  (nav/defpath
   :workspace-monitor
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/monitor"
    :make-props (fn [namespace name]
                  {:workspace-id (utils/restructure namespace name) :tab-name "Monitor"})
    :make-path (fn [workspace-id]
                 (str (ws-path workspace-id) "/monitor"))})
  (nav/defpath
   :workspace-submission
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/monitor/([^/]+)"
    :make-props (fn [namespace name submission-id]
                  {:workspace-id (utils/restructure namespace name) :tab-name "Monitor"
                   :submission-id submission-id})
    :make-path (fn [workspace-id submission-id]
                 (str (ws-path workspace-id) "/monitor/" submission-id))})
  (nav/defpath
   :workspace-workflow
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/monitor/([^/]+)/([^/]+)"
    :make-props (fn [namespace name submission-id workflow-id]
                  {:workspace-id (utils/restructure namespace name) :tab-name "Monitor"
                   :submission-id submission-id :workflow-id workflow-id})
    :make-path (fn [workspace-id submission-id workflow-id]
                 (str (ws-path workspace-id) "/monitor/" submission-id "/" workflow-id))}))
