(ns org.broadinstitute.firecloud-ui.page.workspace.method-config-importer
  (:require
    [dmohs.react :as react]
    clojure.string
    [org.broadinstitute.firecloud-ui.utils :as utils]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table-v2 :as table]
    [org.broadinstitute.firecloud-ui.common.components :as comps]))


(defn- create-mock-methodconfs-import []
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
       :inputs {"Input 1" "[some value]"
                "Input 2" "[some value]"}
       :outputs {"Output 1" "[some value]"
                 "Output 2" "[some value]"}
       :prerequisites ["Predicate 1" "Predicate 2"]})
    (range (rand-int 50))))


(react/defc ImportWorkspaceMethodsConfigurationsList
  {:render
   (fn [{:keys [state props]}]
     [:div {}
      [comps/ModalDialog
       {:width 600
        :content [:div {}
                  [:div {:style {:backgroundColor "#fff"
                                 :borderBottom (str "1px solid " (:line-gray style/colors))
                                 :padding "20px 48px 18px"
                                 :fontSize "137%" :fontWeight 400 :lineHeight 1}}
                   "Import a Method Configuration"]
                  [:div {:style {:padding "22px 48px 40px" :backgroundColor (:background-gray style/colors)}}
                   "Destination Name : "
                   (style/create-text-field {:id "dest_name_id" :style {:ref "destinationName"}})
                   [:br]
                   "Destination Namespace : "
                   (style/create-text-field {:id "dest_namespace_id" :style {:ref "destinationNamespace"}})
                   [:br]
                   [comps/Button
                    {:title-text "Import configuration"
                     :icon :plus
                     :onClick (fn []
                                (utils/rlog "this is where an AJAX call must be made!")
                                (swap! state assoc :show-import-mc-modal? false))}]
                   [comps/Button
                    {:title-text "cancel import"
                     :icon :x
                     :onClick (fn []
                                (swap! state assoc :show-import-mc-modal? false))}]]]
        :show-when (:show-import-mc-modal? @state)}]
      (cond
        (:loaded-import-confs? @state)
        (if (zero? (count (:method-confs @state)))
          (style/create-message-well "There are no method configurations to display for import!")
          [table/Table
           {:columns [{:header "Name" :starting-width 200
                       :content-renderer
                       (fn [row-index conf]
                         [:a
                          {:onClick
                           (fn []
                             (swap! state assoc
                               :selected-conf conf
                               :show-import-mc-modal? true)
                             (let [dest_name_elem (.getElementById js/document (name "dest_name_id"))
                                   method_config_prepopname ((:selected-conf @state) "name")
                                   dest_namespace_elem (.getElementById js/document
                                                         (name "dest_namespace_id"))
                                   method_config_ns_prepopname ((:selected-conf @state) "namespace")]
                               (set! (.-value dest_name_elem) method_config_prepopname)
                               (set! (.-value dest_namespace_elem) method_config_ns_prepopname)))
                           :href "javascript:;"
                           :style {:color
                                   (:button-blue style/colors) :textDecoration "none"}}
                          (conf "name")])}
                      {:header "Namespace" :starting-width 200}
                      {:header "Type" :starting-width 100}
                      {:header "Workspace Name" :starting-width 160}
                      {:header "Method" :starting-width 210}
                      {:header "Config" :starting-width 290}
                      {:header "Inputs" :starting-width 200}
                      {:header "Outputs" :starting-width 200}
                      {:header "Prerequisites" :starting-width 200}]
            :data (map
                    (fn [config]
                      [config
                       (config "namespace")
                       (config "rootEntityType")
                       (clojure.string/join ":"
                         (map #(get-in config ["workspaceName" %]) ["namespace" "name"]))
                       (clojure.string/join ":"
                         (map #(get-in config ["methodStoreMethod" %])
                           ["methodNamespace" "methodName" "methodVersion"]))
                       (clojure.string/join ":"
                         (map #(get-in config ["methodStoreConfig" %])
                           ["methodConfigNamespace" "methodConfigName" "methodConfigVersion"]))
                       (utils/map-to-string (config "inputs"))
                       (utils/map-to-string (config "outputs"))
                       (clojure.string/join "," (config "prerequisites"))])
                    (:method-confs @state))}])
        (:error-message @state) [:div {:style {:color "red"}}
                                 "FireCloud service returned error: " (:error-message @state)]
        :else [comps/Spinner {:text "Loading configurations for import..."}])])
   :component-did-mount
   (fn [{:keys [state props]}]
     (utils/ajax-orch
       (str "/workspaces/" (:selected-workspace-namespace props)
         "/" (:selected-workspace props) "/methodconfigs")
       {:on-done (fn [{:keys [success? xhr]}]
                   (if success?
                     (do
                       (swap! state assoc
                         :loaded-import-confs? true
                         :method-confs (utils/parse-json-string (.-responseText xhr)))
                       )
                     (swap! state assoc :error-message (.-statusText xhr))))
        :canned-response {:responseText (utils/->json-string (create-mock-methodconfs-import))
                          :status 200
                          :delay-ms (rand-int 2000)}}))})


(def modal-import-background
  {:backgroundColor "rgba(82, 129, 197, 0.4)"
   :overflowX "hidden" :overflowY "scroll"
   :position "fixed" :zIndex 9999
   :top 0 :right 0 :bottom 0 :left 0})


(def ^:private modal-import-content
  {:transform "translate(-50%, 0px)"
   :backgroundColor (:background-gray style/colors)
   :position "relative" :marginBottom 60
   :top 60
   :left "50%"
   :width "90%"})


(defn render-import-overlay [state  workspace ]
  (let [clear-import-overlay #(swap! state assoc :import-overlay-shown? false)]
    (when (:import-overlay-shown? @state)
      [:div {:style modal-import-background
             :onKeyDown (common/create-key-handler [:esc] clear-import-overlay)}
       [:div {:style modal-import-content}
        [:div {:style {:position "absolute" :right 2 :top 2}}
         [:div {:style {:backgroundColor (:button-blue style/colors) :color "#fff"
                        :padding "0.5em" :cursor "pointer"}
                :onClick #(swap! state assoc :import-overlay-shown? false)}
          (icons/font-icon {:style {:fontSize "60%"}} :x)]]
        [:div {:style {:backgroundColor "#fff"
                       :borderBottom (str "1px solid " (:line-gray style/colors))
                       :padding "20px 48px 18px"}}
         [:div {:style {:fontSize 24 :align "center" :textAlign "center" :paddingBottom "0.5em"}} "Select A Method Configuration For Import"]
         [ImportWorkspaceMethodsConfigurationsList {:workspace workspace}]
         [:div {:style {:paddingTop "0.5em"}}]]]])))
