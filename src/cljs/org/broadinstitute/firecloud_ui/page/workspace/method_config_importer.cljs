(ns org.broadinstitute.firecloud-ui.page.workspace.method-config-importer
  (:require
    [dmohs.react :as react]
    clojure.string
    [org.broadinstitute.firecloud-ui.utils :as utils]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
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
  {:component-did-mount
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
                          :delay-ms (rand-int 2000)}}))
   :render
   (fn [{:keys [state props]}]
     [:div {}
      [comps/ModalDialog
       {:height 250
        :width 600
        :content [:div {}
                  [:div {:style {:backgroundColor "#fff"
                                 :borderBottom (str "1px solid " (:line-gray style/colors))
                                 :padding "20px 48px 18px"
                                 :fontSize "137%" :fontWeight 400 :lineHeight 1}}
                   "Import a Method Configuration"]
                  [:div {:style {:padding "22px 48px 40px" :backgroundColor (:background-gray style/colors)}}
                   (str "Selected Method Configuration Name : " (:selected-mc-name @state))
                   [:br]
                   (str "Selected Method Configuration Namespace : " (:selected-mc-namespace @state))
                   [:hr]
                   "Destination Name : "
                   (style/create-text-field {:style {:ref "destinationName"}})
                   [:br]
                   "Destination Namespace : "
                   (style/create-text-field {:style {:ref "destinationNamespace"}})
                   [:br]
                   [comps/Button
                    {:icon :plus
                     :onClick (fn [] (swap! state assoc :show-import-mc-modal? false))}]]]
        :show-when (:show-import-mc-modal? @state)
        :dismiss-self (fn [] (swap! state assoc :show-import-mc-modal? false))}]
      (cond
        (:loaded-import-confs? @state)
        (if (zero? (count (:method-confs @state)))
          [:div {:style {:textAlign "center" :backgroundColor (:background-gray style/colors)
                         :padding "1em 0" :margin "0 4em" :borderRadius 8}}
           "There are no method configurations to display for import!"]
          [table/AdvancedTable
           (let [header (fn [children] [:div {:style {:fontWeight 600 :fontSize 13
                                                      :padding "14px 16px"
                                                      :borderLeft "1px solid #777777"
                                                      :color "#fff" :backgroundColor
                                                     (:header-darkgray style/colors)}}
                                        children])
                 cell (fn [children] [:span {:style {:paddingLeft 16}} children])]
             {:columns [{:header-component (header "Name") :starting-width 200
                         :cell-renderer (fn [row-num conf]
                                          (cell [:a {:onClick (fn []
                                                                (swap! state assoc
                                                                  :selected-mc-name (conf "name")
                                                                  :selected-mc-namespace (conf "namespace")
                                                                  :show-import-mc-modal? true))
                                                     :href "javascript:;"
                                                     :style {:color
                                                              (:button-blue style/colors) :textDecoration "none"}}
                                                 (conf "name")]))}
                        {:header-component (header "Namespace") :starting-width 200
                         :cell-renderer (fn [row-num conf] (cell (conf "namespace")))}
                        {:header-component (header "Type") :starting-width 100
                         :cell-renderer (fn [row-num conf] (cell (conf "rootEntityType")))}
                        {:header-component (header "Workspace Name") :starting-width 160
                         :cell-renderer (fn [row-num conf] (cell (str ((conf "workspaceName") "namespace") ":"
                                                                   ((conf "workspaceName") "name"))))}
                        {:header-component (header "Method") :starting-width 210
                         :cell-renderer (fn [row-num conf]
                                          (cell (str
                                                  ((conf "methodStoreMethod") "methodNamespace") ":"
                                                  ((conf "methodStoreMethod") "methodName") ":"
                                                  ((conf "methodStoreMethod") "methodVersion"))))}
                        {:header-component (header "Config") :starting-width 290
                         :cell-renderer (fn [row-num conf]
                                          (cell (str ((conf "methodStoreConfig") "methodConfigNamespace") ":"
                                                     ((conf "methodStoreConfig") "methodConfigName") ":"
                                                      ((conf "methodStoreConfig") "methodConfigVersion"))))}
                        {:header-component (header "Inputs") :starting-width 200
                         :cell-renderer (fn [row-num conf] (cell (utils/map-to-string (conf "inputs"))))}
                        {:header-component (header "Outputs") :starting-width 200
                         :cell-renderer (fn [row-num conf]
                                          (cell (utils/map-to-string (conf "outputs"))))}
                        {:header-component (header "Prerequisites") :starting-width 200
                         :cell-renderer (fn [row-num conf]
                                          (clojure.string/join "," (conf "prerequisites")))}]
              :data (:method-confs @state)
              :row-props (fn [row-num conf]
                           {:style {:fontSize "80%" :fontWeight 500
                                    :paddingTop 10 :paddingBottom 7
                                    :backgroundColor (if (even? row-num)
                                                       (:background-gray style/colors)
                                                       "#fff")}})})])

        (:error-message @state) [:div {:style {:color "red"}}
                                 "FireCloud service returned error: " (:error-message @state)]
        :else [comps/Spinner {:text "Loading configurations for import..."}])])})


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
