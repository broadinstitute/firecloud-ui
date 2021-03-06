(ns broadfcui.page.workspace.details
  (:require
   [dmohs.react :as react]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.components.foundation-tooltip :refer [FoundationTooltip]]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.components.tab-bar :as tab-bar]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.net :as net]
   [broadfcui.page.notifications :as notifications]
   [broadfcui.page.workspace.analysis.tab :as analysis-tab]
   [broadfcui.page.workspace.data.tab :as data-tab]
   [broadfcui.page.workspace.method-configs.tab :as method-configs-tab]
   [broadfcui.page.workspace.monitor.tab :as monitor-tab]
   [broadfcui.page.workspace.notebooks.tab :as notebooks-tab]
   [broadfcui.page.workspace.summary.tab :as summary-tab]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   ))



(defn- protected-banner [workspace]
  (let [this-auth-domain (get-in workspace [:workspace :authorizationDomain])]
    (when (seq this-auth-domain)
      [:div {:style {:paddingTop 2}}
       [:div {:style {:backgroundColor "#ccc"
                      :fontSize "small"
                      :padding "4px 0"
                      :textAlign "center"}
              :data-test-id "auth-domain-restriction-message"}
        "Note: Access to this workspace is restricted to an Authorization Domain"]
       [:div {:style {:height 1 :backgroundColor "#bbb" :marginTop 2}}]])))

(defn- bucket-banner [{:keys [bucket-access? bucket-status-code]}]
  (when (false? bucket-access?)
    [:div {:style {:paddingTop 2}}
     [:div {:style {:backgroundColor "#efdcd7"
                    :fontSize "small"
                    :padding "4px 0"
                    :textAlign "center"}
            :data-test-id "no-bucket-access"}
      (cond (= 404 bucket-status-code)
            (list "The Google bucket associated with this workspace"
                 " does not exist. Please write our "
                (links/create-external {:href (config/forum-url)} "help forum")
                 " for assistance.")
            :else (list "The Google bucket associated with this workspace is currently"
                       " unavailable. This should be resolved shortly. If this persists for"
                       " more than an hour, please write our "
                       (links/create-external {:href (config/forum-url)} "help forum")
                       " for assistance."))]
     [:div {:style {:height 1 :backgroundColor "#efdcd7" :marginTop 2}}]]))


(def ^:private SUMMARY "Summary")
(def ^:private DATA "Data")
(def ^:private ANALYSIS "Analysis")
(def ^:private CONFIGS "Method Configurations")
(def ^:private MONITOR "Monitor")
(def ^:private NOTEBOOKS "Notebooks")

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

(react/defc- WorkspaceDetails
  {:render
   (fn [{:keys [props state locals refs this]}]
     (let [{:keys [workspace-id]} props
           {:keys [workspace workspace-error bucket-access?]} @state
           active-tab (:tab-name props)
           request-refresh #(this :-refresh-workspace)
           refresh-tab #((@refs %) :refresh)
           tabs [[SUMMARY :workspace-summary]
                 [DATA :workspace-data]
                 [ANALYSIS :workspace-analysis]
                 [NOTEBOOKS :workspace-notebooks]
                 [CONFIGS :workspace-method-configs]
                 [MONITOR :workspace-monitor]]]
       [:div {}
        [:div {:style {:minHeight "0.5rem"}}
         (protected-banner workspace)
         (bucket-banner (select-keys @state [:bucket-access? :bucket-status-code]))]
        [:div {:style {:marginTop "1rem" :padding "0 1.5rem"
                       :display "flex" :justifyContent "space-between"}}
         (tab-bar/render-title "WORKSPACE"
                               [:span {}
                                [:span {:data-test-id "header-namespace"} (:namespace workspace-id)]
                                "/"
                                [:span {:data-test-id "header-name"} (:name workspace-id)]])
         [FoundationTooltip
          {:tooltip "Adjust notifications for this workspace"
           :position "left"
           :style {:marginRight "-0.5rem" :borderBottom "none" :alignSelf "center"}
           :data-hover-delay "1000" :data-click-open "false"
           :text
           (dropdown/render-icon-dropdown
            {:icon-name :bell :icon-color (:text-light style/colors)
             :position "bottom"
             :button {:className "float-right"}
             :ref (fn [instance] (swap! locals assoc :infobox instance))
             :contents [notifications/WorkspaceComponent (select-keys props [:workspace-id])]})}]]
        (tab-bar/create-bar (merge {:tabs tabs
                                    :context-id workspace-id
                                    :active-tab (or active-tab SUMMARY)}
                                   (utils/restructure request-refresh refresh-tab)))
        [:div {:style {:marginTop "2rem"}}
         (if-let [error (:workspace-error @state)]
           [:div {:style {:textAlign "center" :color (:state-exception style/colors)}
                  :data-test-id "workspace-details-error"}
            "Error loading workspace: " error]
           (if-not workspace
             [:div {:style {:textAlign "center" :padding "1rem"}}
              (spinner "Loading workspace...")]
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
               NOTEBOOKS (react/create-element
                          [notebooks-tab/Page
                           (merge {:ref NOTEBOOKS}
                                  (utils/restructure workspace-id workspace))])
               CONFIGS (react/create-element
                        [method-configs-tab/Page
                         (merge {:ref CONFIGS
                                 :on-submission-success #(nav/go-to-path :workspace-submission workspace-id %)}
                                (utils/restructure workspace-id workspace bucket-access?)
                                (select-keys props [:config-id]))])
               MONITOR (react/create-element
                        [monitor-tab/Page
                         (merge {:ref MONITOR}
                                (utils/restructure workspace-id workspace)
                                (select-keys props [:submission-id :workflow-id]))]))))]]))
   :component-will-mount
   (fn [{:keys [this]}]
     (this :-refresh-workspace))
   :component-will-receive-props
   (fn [{:keys [props next-props this after-update]}]
     (when (utils/any-change [:workspace-id] props next-props)
       (after-update this :-refresh-workspace)))
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
                     (swap! state assoc :workspace (process-workspace parsed-response))
                     (swap! state assoc :workspace-error (:message parsed-response)))))}))})

(defn- ws-path [ws-id]
  (str "workspaces/" (:namespace ws-id) "/" (:name ws-id)))

(defn add-nav-paths []
  (nav/defpath
   :workspace-summary
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)"
    :terra-redirect (fn [ws-id] (ws-path (:workspace-id ws-id)))
    :make-props (fn [namespace name]
                  {:workspace-id (utils/restructure namespace name)})
    :make-path ws-path})
  (nav/defpath
   :workspace-data
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/data"
    :terra-redirect (fn [ws-id] (str (ws-path (:workspace-id ws-id)) "/data"))
    :make-props (fn [namespace name]
                  {:workspace-id (utils/restructure namespace name) :tab-name "Data"})
    :make-path (fn [workspace-id]
                 (str (ws-path workspace-id) "/data"))})
  (nav/defpath
   :workspace-analysis
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/analysis"
    ;; Terra UI does not have an analysis tab, so redirect to the root of that workspace.
    :terra-redirect (fn [ws-id] (ws-path (:workspace-id ws-id)))
    :make-props (fn [namespace name]
                  {:workspace-id (utils/restructure namespace name) :tab-name "Analysis"})
    :make-path (fn [workspace-id]
                 (str (ws-path workspace-id) "/analysis"))})
  (nav/defpath
   :workspace-method-configs
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/method-configs"
    :terra-redirect (fn [ws-id] (str (ws-path (:workspace-id ws-id)) "/tools"))
    :make-props (fn [namespace name]
                  {:workspace-id (utils/restructure namespace name)
                   :tab-name "Method Configurations"})
    :make-path (fn [workspace-id]
                 (str (ws-path workspace-id) "/method-configs"))})
  (nav/defpath
   :workspace-method-config
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/method-configs/([^/]+)/([^/]+)"
    :terra-redirect (fn [args]
                      (str (ws-path (:workspace-id args)) "/tools/"
                           (get-in args [:config-id :namespace]) "/"
                           (get-in args [:config-id :name])))
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
    :terra-redirect (fn [ws-id] (str (ws-path (:workspace-id ws-id)) "/job_history"))
    :make-props (fn [namespace name]
                  {:workspace-id (utils/restructure namespace name) :tab-name "Monitor"})
    :make-path (fn [workspace-id]
                 (str (ws-path workspace-id) "/monitor"))})
  (nav/defpath
   :workspace-submission
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/monitor/([^/]+)"
    :terra-redirect (fn [args] (str (ws-path (:workspace-id args))  "/job_history/" (:submission-id args)))
    :make-props (fn [namespace name submission-id]
                  {:workspace-id (utils/restructure namespace name) :tab-name "Monitor"
                   :submission-id submission-id})
    :make-path (fn [workspace-id submission-id]
                 (str (ws-path workspace-id) "/monitor/" submission-id))})
  (nav/defpath
   :workspace-workflow
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/monitor/([^/]+)/([^/]+)"
    ;; this redirect for workflow details intentionally links to Terra's submission detail page, not the
    ;; workflow detail.  Workflow details in Terra are provided by job manager currently. We are ok linking
    ;; to the submission detail instead.
    :terra-redirect (fn [args] (str (ws-path (:workspace-id args))  "/job_history/" (:submission-id args)))
    :make-props (fn [namespace name submission-id workflow-id]
                  {:workspace-id (utils/restructure namespace name) :tab-name "Monitor"
                   :submission-id submission-id :workflow-id workflow-id})
    :make-path (fn [workspace-id submission-id workflow-id]
                 (str (ws-path workspace-id) "/monitor/" submission-id "/" workflow-id))})
   (nav/defpath
   :workspace-notebooks
   {:component WorkspaceDetails
    :regex #"workspaces/([^/]+)/([^/]+)/notebooks"
    :terra-redirect (fn [ws-id] (str (ws-path (:workspace-id ws-id)) "/notebooks"))
    :make-props (fn [namespace name]
                  {:workspace-id (utils/restructure namespace name) :tab-name "Notebooks"})
    :make-path (fn [workspace-id]
                 (str (ws-path workspace-id) "/notebooks"))}))
