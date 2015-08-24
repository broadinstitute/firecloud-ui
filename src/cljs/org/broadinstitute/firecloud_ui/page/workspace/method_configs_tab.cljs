(ns org.broadinstitute.firecloud-ui.page.workspace.method-configs-tab
  (:require
    [dmohs.react :as react]
    clojure.string
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.page.workspace.method-config-importer :as importmc]
    [org.broadinstitute.firecloud-ui.page.workspace.method-config-editor :refer [MethodConfigEditor]]
    [org.broadinstitute.firecloud-ui.paths :as paths]
    ;; TODO(dmohs): No need to refer these. Having utils available is enough.
    [org.broadinstitute.firecloud-ui.utils :as utils :refer [rlog jslog cljslog]]))


(defn- create-mock-methodconfs []
  (map
    (fn [i]
      {:name (str "Configuration " (inc i))
       :namespace (rand-nth ["Broad" "nci" "public"])
       :rootEntityType "Task"
       :workspaceName {:namespace (str "ws_ns_" (inc i))
                       :name (str "ws_n_" (inc i))}
       :methodStoreMethod {:methodNamespace (str "ms_ns_" (inc i))
                           :methodName (str "ms_n_" (inc i))
                           :methodVersion (str "ms_v_" (inc i))}
       :methodStoreConfig {:methodConfigNamespace (str "msc_ns_" (inc i))
                           :methodConfigName (str "msc_n_" (inc i))
                           :methodConfigVersion (str "msc_v_" (inc i))}
       ;; Real data doesn't have the following fields, but for mock data we carry the same
       ;; objects around, so initialize them here for convenience
       :inputs {"Input 1" "[some value]"
                "Input 2" "[some value]"}
       :outputs {"Output 1" "[some value]"
                 "Output 2" "[some value]"}
       :prerequisites {"unused 1" "Predicate 1"
                       "unused 2" "Predicate 2"}})
    (range (rand-int 50))))





(react/defc MethodConfigurationsList
  {:render
   (fn [{:keys [props state]}]
     [:div {}
      (when (:show-import-overlay? @state)
        (importmc/render-import-overlay (:workspace-id props)
         #(swap! state dissoc :show-import-overlay?)
         #(swap! state dissoc :show-import-overlay? :server-response)))
      [:div {:style {:float "right" :padding "0 2em 1em 0"}}
       [comps/Button {:text "Import Configuration ..."
                      :onClick #(swap! state assoc :show-import-overlay? true)}]]
      (common/clear-both)
      (let [server-response (:server-response @state)
            {:keys [configs error-message]} server-response]
        (cond
          (nil? server-response) [comps/Spinner {:text
                                                 (if (:mc-for-deletion @state)
                                                   (let [rm_url
                                                         (paths/rm-method-configuration-path
                                                           (:workspace-id props)
                                                           (:mc-for-deletion @state))
                                                         url-map {:method "DELETE"
                                                                  :on-done (fn [{:keys [success? xhr]}]
                                                                             (if success?
                                                                               ;((:on-import props))
                                                                               (swap! state assoc
                                                                                 :server-response
                                                                                 nil
                                                                                 :mc-for-deletion
                                                                                 nil
                                                                                 )
                                                                               (swap! state assoc
                                                                                 :server-response
                                                                                 (.-statusText xhr)
                                                                                 :mc-for-deletion
                                                                                 nil)
                                                                               ))}]
                                                     (do
                                                       (utils/ajax-orch rm_url url-map)
                                                       (str "Deleting configuration...")))
                                                   "Loading configurations...")}]
          error-message (style/create-server-error-message error-message)
          (zero? (count configs))
          (style/create-message-well "There are no method configurations to display.")
          :else
          [table/Table
           {:columns
            [{:header "Name" :starting-width 200 :sort-by #(% "name") :filter-by #(% "name")
              :content-renderer
              (fn [row-index config]
                [:div {}
                [:a {:href "javascript:;"
                     :style {:color (:button-blue style/colors) :textDecoration "none"}
                     :onClick #((:on-config-selected props) config)}
                 (config "name")]
                [:a {:href "javascript:;" :style {:color (:exception-red style/colors)
                                                  :float "right" :align "right"}
                     :onClick (fn [e]
                                (let [confirm_status (js/confirm "Are you sure?")]
                                  (if confirm_status
                                    (swap! state assoc :server-response nil
                                      :mc-for-deletion config)
                                    nil)))}
                 (icons/font-icon {:style {:fontSize "135%"}} :trash-can)]])}
             {:header "Namespace" :starting-width 200 :sort-by :value}
             {:header "Root Entity Type" :starting-width 140 :sort-by :value}
             {:header "Workspace" :starting-width 200}
             {:header "Method Store Method" :starting-width 300}
             {:header "Method Store Configuration" :starting-width 300}]
            :data (map
                   (fn [config]
                     [config
                      (config "namespace")
                      (config "rootEntityType")
                      (clojure.string/join
                       ":" (map #(get-in config ["workspaceName" %]) ["namespace" "name"]))
                      (clojure.string/join
                       ":" (map #(get-in config ["methodStoreMethod" %])
                                ["methodNamespace" "methodName" "methodVersion"]))
                      (clojure.string/join
                       ":" (map #(get-in config ["methodStoreConfig" %])
                                ["methodConfigNamespace" "methodConfigName" "methodConfigVersion"]))])
                   configs)}]))])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-method-configs this))
   :component-did-update
   (fn [{:keys [this state]}]
     (when (and
             (nil? (:server-response @state)) (nil? (:mc-for-deletion @state))  )
       (react/call :load-method-configs this)))
   :load-method-configs
   (fn [{:keys [props state]}]
     (utils/call-ajax-orch (paths/list-method-configs-path (:workspace-id props))
       {:on-success (fn [{:keys [parsed-response]}]
                      (swap! state assoc :server-response {:configs (vec parsed-response)}))
        :on-failure (fn [{:keys [status-text]}]
                      (swap! state assoc :server-response {:error-message status-text}))
        :mock-data (create-mock-methodconfs)}))})


(react/defc Page
  {:render
   (fn [{:keys [props state]}]
     [:div {:style {:padding "1em 0"}}
      (if (:selected-method-config @state)
        [MethodConfigEditor {:workspace-id (:workspace-id props)
                             :config (:selected-method-config @state)}]
        [MethodConfigurationsList
         {:workspace-id (:workspace-id props)
          :on-config-selected (fn [config]
                                (swap! state assoc :selected-method-config config))}])])
   :component-will-receive-props
   (fn [{:keys [state]}]
     (swap! state dissoc :selected-method-config))})


(defn render [workspace-id]
  [Page {:workspace-id workspace-id}])
