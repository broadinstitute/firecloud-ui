(ns broadfcui.page.method-repo.method.details
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.components.tab-bar :as tab-bar]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.net :as net]
   [broadfcui.utils :as utils]
   ))


(def ^:private SUMMARY "Summary")
(def ^:private WDL "WDL")
(def ^:private CONFIGS "Configurations")

(react/defc- MethodDetails
  {:render
   (fn [{:keys [props state refs this]}]
     (let [{:keys [method-id]} props
           {:keys [method method-error bucket-access?]} @state
           active-tab (:tab-name props)
           request-refresh #(this :-refresh-method)
           refresh-tab #((@refs %) :refresh)]
       [:div {}
        [:div {:style {:marginTop "1.5rem" :padding "0 1.5rem"
                       :display "flex" :justifyContent "space-between"}}
         (tab-bar/render-title "METHOD"
                               [:span {}
                                [:span {:data-test-id (config/when-debug "header-namespace")} (:namespace method-id)]
                                "/"
                                [:span {:data-test-id (config/when-debug "header-name")} (:name method-id)]])]
        (tab-bar/create-bar (merge {:tabs [[SUMMARY :method-summary]
                                           [WDL :method-wdl]
                                           [CONFIGS :method-configs]]
                                    :context-id method-id
                                    :active-tab (or active-tab SUMMARY)}
                                   (utils/restructure request-refresh refresh-tab)))
        [:div {:style {:marginTop "2rem"}}
         (if-let [error (:method-error @state)]
           [:div {:style {:textAlign "center" :color (:exception-state style/colors)}
                  :data-test-id (config/when-debug "method-details-error")}
            "Error loading method: " error]
           (if-not method
             [:div {:style {:textAlign "center" :padding "1rem"}}
              [comps/Spinner {:text "Loading method..."}]]
             #_(condp = active-tab
               nil (react/create-element
                    [summary-tab/Summary
                     (merge {:key method-id :ref SUMMARY}
                            (utils/restructure method-id method request-refresh bucket-access?))])
               WDL (react/create-element
                     [data-tab/WorkspaceData
                      (merge {:ref WDL}
                             (utils/restructure method-id method method-error request-refresh))])
               CONFIGS (react/create-element
                        [method-configs-tab/Page
                         (merge {:ref CONFIGS
                                 :on-submission-success #(nav/go-to-path :method-submission method-id %)}
                                (utils/restructure method-id method request-refresh bucket-access?)
                                (select-keys props [:config-id]))]))))]]))
   :component-will-mount
   (fn [{:keys [this]}]
     (this :-refresh-method))
   :component-will-receive-props
   (fn [{:keys [this after-update]}]
     (after-update this :-refresh-method))
   :-refresh-method
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/check-bucket-read-access (:method-id props))
       :on-done (fn [{:keys [status-code success?]}]
                  (swap! state assoc :bucket-status-code status-code :bucket-access? success?))})
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-method-snapshots (:method-id props))
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? parsed-response]}]
                   (if success?
                     (swap! state assoc :method (parsed-response))
                     (swap! state assoc :method-error (:message parsed-response)))))}))})

(defn- ws-path [ws-id]
  (str "methods/" (:namespace ws-id) "/" (:name ws-id)))

(defn add-nav-paths []
  (nav/defpath
   :method-summary
   {:component MethodDetails
    :regex #"methods/([^/]+)/([^/]+)"
    :make-props (fn [namespace name]
                  {:method-id (utils/restructure namespace name)})
    :make-path ws-path})
  (nav/defpath
   :method-wdl
   {:component MethodDetails
    :regex #"methods/([^/]+)/([^/]+)/wdl"
    :make-props (fn [namespace name]
                  {:method-id (utils/restructure namespace name) :tab-name "WDL"})
    :make-path (fn [method-id]
                 (str (ws-path method-id) "/wdl"))})
  (nav/defpath
   :method-configs
   {:component MethodDetails
    :regex #"methods/([^/]+)/([^/]+)/configs"
    :make-props (fn [namespace name]
                  {:method-id (utils/restructure namespace name)
                   :tab-name "Configurations"})
    :make-path (fn [method-id]
                 (str (ws-path method-id) "/configs"))})
  (nav/defpath
   :method-config
   {:component MethodDetails
    :regex #"methods/([^/]+)/([^/]+)/configs/([^/]+)/([^/]+)"
    :make-props (fn [namespace name config-ns config-name]
                  {:method-id (utils/restructure namespace name) :tab-name "Configurations"
                   :config-id {:namespace config-ns :name config-name}})
    :make-path (fn [method-id config-id]
                 (str (ws-path method-id) "/configs/"
                      (:namespace config-id) "/" (:name config-id)))}))
