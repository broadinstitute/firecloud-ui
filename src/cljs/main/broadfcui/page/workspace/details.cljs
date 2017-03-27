(ns broadfcui.page.workspace.details
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.workspace.analysis.tab :as analysis-tab]
   [broadfcui.page.workspace.data.tab :as data-tab]
   [broadfcui.page.workspace.method-configs.tab :as method-configs-tab]
   [broadfcui.page.workspace.monitor.tab :as monitor-tab]
   [broadfcui.page.workspace.summary.tab :as summary-tab]
   [broadfcui.utils :as utils]
   ))


(react/defc ProtectedBanner
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [workspace]} props]
       (when (and workspace (get-in workspace [:workspace :realm]))
         [:div {:style {:paddingTop 2}}
          [:div {:style {:backgroundColor "#ccc"
                         :fontSize "small"
                         :padding "4px 0"
                         :textAlign "center"}}
           "This is a " [:b {} "restricted"] " workspace for TCGA Controlled Access Data."]
          [:div {:style {:height 1 :backgroundColor "#bbb" :marginTop 2}}]])))})

(react/defc BucketBanner
  {:render
   (fn [{:keys [props]}]
     (when (= false (:bucket-access? props))
       [:div {:style {:paddingTop 2}}
        [:div {:style {:backgroundColor "#efdcd7"
                       :fontSize "small"
                       :padding "4px 0"
                       :textAlign "center"}}
         (cond (= 404 (:bucket-status-code props))
           (str "The Google bucket associated with this workspace"
                " does not exist. Please contact help@firecloud.org.")
           :else (str "The Google bucket associated with this workspace is currently"
                      " unavailable. This should be resolved shortly. If this persists for"
                      " more than an hour, please contact help@firecloud.org."))]
        [:div {:style {:height 1 :backgroundColor "#efdcd7" :marginTop 2}}]]))})


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
        workspace-attributes (->> attributes
                                  (remove (fn [[k _]]
                                            (or (= k :description)
                                                (utils/contains (name k) ":"))))
                                  (into {}))]
    (-> raw-workspace
        (update :workspace dissoc :attributes)
        (assoc-in [:workspace :description] (:description attributes))
        (assoc-in [:workspace :workspace-attributes] workspace-attributes)
        (assoc-in [:workspace :library-attributes] library-attributes))))

(react/defc Tab
  {:render
   (fn [{:keys [props state]}]
     [:a {:style {:flex "0 0 auto" :padding "1em 2em"
                  :borderLeft (when (:first? props) style/standard-line)
                  :borderRight style/standard-line
                  :backgroundColor (when (:active? props) "white")
                  :cursor "pointer" :textDecoration "none" :color "inherit"
                  :position "relative"}
          :href (:href props)
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
                    (swap! state assoc :workspace (process-workspace (get-parsed-response)))
                    (swap! state assoc :workspace-error status-text)))}))
   :render
   (fn [{:keys [props state refs this]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           workspace-id (:workspace-id props)
           {:keys [workspace workspace-error bucket-access?]} @state
           active-tab (:segment nav-context)
           is-active? (fn [tab] (if (= tab SUMMARY) (= active-tab "") (= tab active-tab)))
           make-tab (fn [text on-active-tab-clicked]
                      [Tab {:text text :first? (= text SUMMARY) :active? (is-active? text)
                            :href (if (= text SUMMARY)
                                    (nav/create-href (:nav-context props))
                                    (nav/create-href (:nav-context props) text))
                            :on-active-tab-clicked on-active-tab-clicked}])]
       [:div {}
        [:div {:style {:marginTop "-1.5rem" :minHeight "0.5rem"}}
         [ProtectedBanner (select-keys @state [:workspace :workspace-error])
          {:workspace {:workspace {:realm true}}}]
         [BucketBanner (select-keys @state [:bucket-access? :bucket-status-code])
          {:bucket-access? false}]]
        [:div {:style {:marginTop "1rem" :paddingLeft "1.5rem" :fontSize "125%"}}
         "Workspace: "
         [:span {:style {:fontWeight 500}}
          (:namespace workspace-id) "/" (:name workspace-id)]]
        [:div {:style {:marginTop "1rem"
                       :display "flex" :backgroundColor (:background-light style/colors)
                       :borderTop style/standard-line :borderBottom style/standard-line
                       :padding "0 1.5rem" :justifyContent "space-between"}}
         [:div {:style {:display "flex"}}
          (make-tab SUMMARY #(react/call :refresh-workspace this))
          (make-tab DATA #(react/call :refresh-workspace this))
          (make-tab ANALYSIS #(react/call :refresh (@refs ANALYSIS)))
          (make-tab CONFIGS #(react/call :refresh (@refs CONFIGS)))
          (make-tab MONITOR #(react/call :refresh (@refs MONITOR)))]
         (when (= active-tab ANALYSIS)
           (analysis-tab/render-track-selection-button #(@refs ANALYSIS)))]
        [:div {:style {:marginTop "2rem"}}
         (if-let [error (:workspace-error @state)]
           [:div {:style {:textAlign "center" :color (:exception-state style/colors)}}
            "Error loading workspace: " error]
           (condp = active-tab
             "" (react/create-element
                 [summary-tab/Summary {:key workspace-id :ref SUMMARY
                                       :workspace-id workspace-id
                                       :workspace workspace
                                       :request-refresh #(react/call :refresh-workspace this)
                                       :bucket-access? bucket-access?
                                       :nav-context nav-context
                                       :on-delete (:on-delete props)
                                       :on-clone (:on-clone props)}])
             DATA (react/create-element
                   [data-tab/WorkspaceData {:ref DATA
                                            :workspace-id workspace-id
                                            :workspace workspace
                                            :workspace-error workspace-error
                                            :request-refresh #(this :refresh-workspace)}])
             ANALYSIS (react/create-element
                       [analysis-tab/Page {:ref ANALYSIS :workspace-id workspace-id}])
             CONFIGS (react/create-element
                      [method-configs-tab/Page
                       {:ref CONFIGS
                        :workspace-id workspace-id
                        :workspace workspace
                        :request-refresh #(react/call :refresh-workspace this)
                        :bucket-access? bucket-access?
                        :on-submission-success #(nav/navigate (:nav-context props) MONITOR %)
                        :nav-context (nav/terminate-when (not= active-tab CONFIGS) nav-context)}])
             MONITOR (react/create-element
                      [monitor-tab/Page
                       {:ref MONITOR
                        :workspace-id workspace-id
                        :workspace workspace
                        :nav-context (nav/terminate-when
                                      (not= active-tab MONITOR) nav-context)}])))]]))
   :component-did-mount
   (fn [{:keys [props this]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           tab (:segment nav-context)]
       ;; These tabs don't request a refresh, so if we nav straight there then we need to kick one
       ;; off.
       (when (#{ANALYSIS CONFIGS MONITOR} tab)
         (react/call :refresh-workspace this))))})
