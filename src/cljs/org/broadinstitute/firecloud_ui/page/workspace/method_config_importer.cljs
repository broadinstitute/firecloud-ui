(ns org.broadinstitute.firecloud-ui.page.workspace.method-config-importer
  (:require
    [dmohs.react :as react]
    clojure.string
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.utils :as utils]))


(defn create-path [workspaceNamespace workspaceName]
  (str "/" workspaceNamespace "/"  workspaceName  "/methodconfigs/copyFromMethodRepo"))


(defn create-post-data
  [selected-conf
   selected-conf-dest-name
   selected-conf-dest-ns
   dest-workspace-name
   dest-workspace-namespace]
  (let [destination-map
        {"name" selected-conf-dest-name
        "namespace" selected-conf-dest-ns
        "workspaceName"
        {"name" dest-workspace-namespace
        "namespace" dest-workspace-name}}
        param-map {"methodRepoNamespace" (get-in selected-conf
                                           ["methodStoreMethod" "methodNamespace"])
                   "methodRepoName" (get-in selected-conf
                                      ["methodStoreMethod" "methodName"])
                   "methodRepoSnapshotId" (get-in selected-conf
                                            ["methodStoreMethod" "methodVersion"])
                   "destination" destination-map}]
    param-map))


(defn- create-mock-methodconfs-import []
  (map
    (fn [i]
      {:name (str "Configuration " (inc i))
       :url (str "http://agora-ci.broadinstitute.org/configurations/joel_test/jt_test_config/1")
       :namespace (rand-nth ["Broad" "nci" "public" "ISB"])
       :snapshotId (rand-nth (range 100))
       :synopsis (str (rand-nth ["variant caller synopsis","gene analyzer synopsis","mutect synopsis"]) " " (inc i))
       :createDate (str "20"(inc i) "-06-10T16:54:26Z")
       :owner (rand-nth ["thibault@broadinstitute.org" "esalinas@broadinstitute.org"  ])})
    (range (rand-int 50))))


(defn render-import-modal [state props refs]
  [comps/ModalDialog
   {:width 600
    :content (react/create-element
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
                   {:defaultValue (get-in @state [:selected-conf "name"])
                    :ref "destinationName"})
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
                           dest-name-value (.-value
                                             (.getDOMNode (@refs "destinationName")))
                           dest-namespace-value (.-value (.getDOMNode
                                                           (@refs "destinationNamespace")))
                           dest-workspace-name (get workspace "name")
                           dest-workspace-namespace (get workspace "namespace")
                           methodRepoQuery (create-post-data
                                             (:selected-conf @state)
                                             dest-name-value
                                             dest-namespace-value
                                             dest-workspace-name
                                             dest-workspace-namespace)]
                       (swap! state assoc :show-import-mc-modal? false)
                       (utils/ajax-orch
                         (create-path
                           dest-workspace-namespace
                           dest-workspace-name)
                         {:method :post
                          :data (utils/->json-string methodRepoQuery)
                          :canned-response {:status 201
                                            :delay-ms (rand-int 2000)}
                          :on-done (fn [{:keys [success? xhr]}]
                                     (if success?
                                       (utils/rlog  (str "success in import! "
                                                      "Here, perhaps trigger re-render "
                                                      "of the MCs in the current "
                                                      "workspace?"))
                                       (let [returned-err-msg
                                             {:message (.-statusText xhr)}]
                                         (js/alert
                                           (str "Error in Method Configuration Import : "
                                             returned-err-msg)))))})))}]
                 [comps/Button
                  {:title-text "cancel import"
                   :icon :x
                   :onClick (fn []
                              (swap! state assoc :show-import-mc-modal? false))}]]])
    :show-when true}])


(react/defc ImportWorkspaceMethodsConfigurationsList
  {:render
   (fn [{:keys [state props refs]}]
     [:div {}
      (when (:show-import-mc-modal? @state)
        (render-import-modal state props refs))
      (cond
        (:loaded-import-confs? @state)
        (if (zero? (count (:method-confs @state)))
          (style/create-message-well "There are no method configurations to display for import!")
          [table/Table
           {:columns [{:header "Name" :starting-width 200 :filter-by #(% "name")
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
                      {:header "Snapshot Id" :starting-width 100}
                      {:header "Synopsis" :starting-width 160}
                      {:header "Create Date" :starting-width 210}
                      {:header "Owner" :starting-width 290}]
            :data (map
                    (fn [config]
                      [config
                       (config "namespace")
                       (config "snapshotId")
                       (config "synopsis")
                       (config "createDate")
                       (config "owner")])
                    (:method-confs @state))}])
        (:error-message @state) [:div {:style {:color "red"}}
                                 "FireCloud service returned error: " (:error-message @state)]
        :else [comps/Spinner {:text "Loading configurations for import..."}])])
   :component-did-mount
   (fn [{:keys [state props]}]
     (utils/ajax-orch (str "/configurations")
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
         [:div {:style {:fontSize 24 :align "center" :textAlign "center" :paddingBottom "0.5em"}}
          "Select A Method Configuration For Import"]
         [ImportWorkspaceMethodsConfigurationsList {:workspace workspace}]
         [:div {:style {:paddingTop "0.5em"}}]]]])))
