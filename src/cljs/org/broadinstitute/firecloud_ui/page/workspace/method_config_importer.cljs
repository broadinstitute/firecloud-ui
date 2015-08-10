(ns org.broadinstitute.firecloud-ui.page.workspace.method-config-importer
  (:require
    [dmohs.react :as react]
    clojure.string
    [org.broadinstitute.firecloud-ui.utils :as utils :refer [rlog jslog cljslog]]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.components :as comps]))



(defn create-import-mc-url-path [workspaceNamespace workspaceName]
  (str "/" workspaceNamespace "/"  workspaceName  "/methodconfigs/copyFromMethodRepo"))


(defn create-import-mc-url-post-data
  [selected-conf
   selected-conf-dest-name
   selected-conf-dest-ns
   dest-workspace-name
   dest-workspace-namespace]
  (let [method-store-conf-conf-obj (selected-conf "methodStoreConfig")
        method-store-conf-conf-obj-name (method-store-conf-conf-obj "methodConfigName" )
        method-store-conf-conf-obj-namespace (method-store-conf-conf-obj "methodConfigNamespace")
        method-store-conf-conf-obj-version (method-store-conf-conf-obj "methodConfigVersion" )
        method-store-method-conf-obj (selected-conf "methodStoreMethod" )
        method-store-method-conf-obj-name (method-store-method-conf-obj "methodName" )
        method-store-method-conf-obj-namespace (method-store-method-conf-obj "methodNamespace")
        method-store-method-conf-obj-version (method-store-method-conf-obj "methodVersion")
        methodRepoNamespace method-store-method-conf-obj-namespace
        methodRepoName method-store-method-conf-obj-name
        methodRepoSnapshotId method-store-method-conf-obj-version
        destination-map
        {"name" selected-conf-dest-name
         "namespace" selected-conf-dest-ns
         "workspaceName"
         {"name" dest-workspace-namespace
          "namespace" dest-workspace-name
          }
         }
        param-map {
                   "methodRepoNamespace" methodRepoNamespace
                   "methodRepoName" methodRepoName
                   "methodRepoSnapshotId" methodRepoSnapshotId
                   "destination" destination-map
                   }]
    param-map))


(defn- create-mock-methodconfs-import []
  (map
    (fn [i]
      {:name (str "Configuration " (inc i))
       :namespace (rand-nth ["Broad" "nci" "public"])
       :rootEntityType "Task"
       :workspaceName {:namespace (str "ws-ns-" (inc i))
                       :name (str "ws-n-" (inc i))}
       :methodStoreMethod {:methodNamespace (str "ms-ns-" (inc i))
                           :methodName (str "ms-n-" (inc i))
                           :methodVersion (str "ms-v-" (inc i))}
       :methodStoreConfig {:methodConfigNamespace (str "msc-ns-" (inc i))
                           :methodConfigName (str "msc-n-" (inc i))
                           :methodConfigVersion (str "msc-v-" (inc i))}
       :inputs {"Input 1" "[some value]"
                "Input 2" "[some value]"}
       :outputs {"Output 1" "[some value]"
                 "Output 2" "[some value]"}
       :prerequisites ["Predicate 1" "Predicate 2"]})
    (range (rand-int 50))))



(defn render-import-modal [state props refs]
  [comps/ModalDialog
   {:width 600
    :content
    [:div {}
     [:div {:style {:backgroundColor "#fff"
                    :borderBottom (str "1px solid " (:line-gray style/colors))
                    :padding "20px 48px 18px"
                    :fontSize "137%" :fontWeight 400 :lineHeight 1}}
      "Import a Method Configuration"]
     [:div {:style {:padding "22px 48px 40px"
                    :backgroundColor (:background-gray style/colors)}}
      "Destination Name : "
      (style/create-text-field
        {:defaultValue (get-in @state [:selected-conf "name"]) :ref "destinationName"})
      [:br]
      "Destination Namespace : "
      (style/create-text-field
        {:defaultValue (get-in @state [:selected-conf "namespace"])
         :ref "destinationNamespace"})
      [:br]
      [comps/Button
       {:title-text "Import configuration"
        :icon :plus
        :onClick
        (fn []
          (let [workspace (:workspace props)
                dest-name-value (-> (@refs "destinationName") .getDOMNode .-value)
                dest-namespace-value (-> (@refs "destinationNamespace") .getDOMNode .-value)
                dest-workspace-name (get workspace "name")
                dest-workspace-namespace (get workspace "namespace")
                methodRepoQuery (create-import-mc-url-post-data
                                  (:selected-conf @state)
                                  dest-name-value
                                  dest-namespace-value
                                  dest-workspace-name
                                  dest-workspace-namespace)
                url-method :post
                url-path (create-import-mc-url-path
                           dest-workspace-namespace
                           dest-workspace-name)
                url-data (utils/->json-string methodRepoQuery)
                url-canned-response {:status 201
                                     :delay-ms (rand-int 2000)}
                url-on-done-func (fn [{:keys [success? xhr]}]
                                   (if success?
                                     (utils/rlog "success in import!
                                                           Here, perhaps trigger re-render
                                                           of the MCs in the current
                                                           workspace?")
                                     (let [
                                           returned-err-msg
                                           {:message (.-statusText xhr)}]
                                       (js/alert
                                         (str "Error in Method Configuration Import : "
                                           returned-err-msg)))))]
            (swap! state assoc :show-import-mc-modal? false)
            (utils/ajax-orch
              url-path
              {:url url-path
               :method url-method
               :data url-data
               :canned-response url-canned-response
               :on-done url-on-done-func})))}]
      [comps/Button
       {:title-text "cancel import"
        :icon :x
        :onClick (fn []
                   (swap! state assoc :show-import-mc-modal? false))}]]]
    :show-when true}]
  )





(react/defc ImportWorkspaceMethodsConfigurationsList
  {:render
   (fn [{:keys [state props refs]}]
     [:div {}
      (when (:show-import-mc-modal? @state)
        (render-import-modal state props refs)
        )
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
                             (swap! state assoc :selected-conf conf :show-import-mc-modal? true))
                           :href "javascript:;"
                           :style {:color (:button-blue style/colors) :textDecoration "none"}}
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
