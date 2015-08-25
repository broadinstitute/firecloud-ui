(ns org.broadinstitute.firecloud-ui.page.workspace.method-config-importer
  (:require
    [dmohs.react :as react]
    clojure.string
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.paths :as paths]
    [org.broadinstitute.firecloud-ui.utils :as utils]))


(defn create-post-data
  [selected-conf-name
   selected-conf-ns
   selected-conf-snapId
   dest-name
   dest-namespace]
  {"configurationNamespace" selected-conf-ns
   "configurationName" selected-conf-name
   "configurationSnapshotId" (str selected-conf-snapId)
   "destinationNamespace" dest-namespace
   "destinationName" dest-name})

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


(react/defc ModalImportOptionsAndButton
  {:render (fn [{:keys [state props refs]}]
             (let [selected-conf-name (get  props :init-Name)
                   selected-conf-namespace (get props :init-Namespace)
                   selected-conf-snapshotId (get props :init-SnapshotId)
                   workspace-id (:workspace-id props)
                   dest-workspace-name (:name workspace-id)
                   dest-workspace-namespace (:namespace workspace-id)]
               [:div {:style {:backgroundColor (:background-gray style/colors)}}
                [:div {}
                 "Destination Name : "
                 (style/create-text-field {:defaultValue selected-conf-name :ref "destinationName"})]
                "Destination Namespace : "
                (style/create-text-field
                  {:defaultValue selected-conf-namespace
                   :ref "destinationNamespace"})
                [:br]
                [comps/Button
                 {:title-text "import selected"
                  :icon :plus
                  :onClick (fn []
                             (let [dest-conf-name (.-value (.getDOMNode (@refs "destinationName")))
                                   dest-conf-namespace (.-value (.getDOMNode (@refs "destinationNamespace")))
                                   post-data (create-post-data
                                               selected-conf-name
                                               selected-conf-namespace
                                               selected-conf-snapshotId
                                               dest-conf-name
                                               dest-conf-namespace)]
                               (utils/ajax-orch
                                 (paths/copy-method-config-to-workspace-path workspace-id)
                                 {:headers {"Content-Type" "application/json"}
                                  :canned-response {:responseText
                                                      (utils/->json-string (create-mock-methodconfs-import))
                                                    :status 200
                                                    :delay-ms (rand-int 2000)}
                                  :method :post
                                  :data (utils/->json-string post-data)
                                  :on-done (fn [{:keys [success? xhr]}]
                                             (if success?
                                               ((:on-import props))
                                               (js/alert (str "Error in import : "  (.-statusText xhr)))))})))}]]))})


(defn render-import-modal [config props refs on-close on-import]
  [comps/Dialog
   {:width 750
    :dismiss-self on-close
    :content (react/create-element
               [:div {}
                [:div {:style {:backgroundColor "#fff"
                               :borderBottom (str "1px solid " (:line-gray style/colors))
                               :padding "20px 48px 18px"
                               :fontSize "137%" :fontWeight 400 :lineHeight 1}}
                 "Import a Method Configuration"
                 [:hr]
                 (config "name") [:br]
                 (config "namespace") [:br]
                 (config "snapshotId")]
                 [:div {:style {:position "absolute" :right 2 :top 2}}
                  [:div {:style {:backgroundColor (:button-blue style/colors) :color "#fff"
                                 :padding "0.5em" :cursor "pointer"}
                         :onClick on-close}
                   (icons/font-icon {:style {:fontSize "60%"}} :x)]]
                [:div {:style {:padding "22px 48px 40px"
                               :backgroundColor (:background-gray style/colors)}}
                 [ModalImportOptionsAndButton {:init-Name (config "name")
                                               :init-Namespace (config "namespace")
                                               :init-SnapshotId (config "snapshotId")
                                               :on-import on-import
                                               :workspace-id (:workspace-id props)}]]])}])


(react/defc ImportWorkspaceMethodsConfigurationsList
  {:render
   (fn [{:keys [state props refs]}]
     [:div {}
      (when (:show-import-mc-modal? @state)
        (render-import-modal (:selected-conf @state) props refs
                             #(swap! state dissoc :show-import-mc-modal?)
                             (fn [& args]
                               (swap! state dissoc :show-import-mc-modal?)
                               (apply (:on-import props) args))))
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
   (fn [{:keys [state]}]
     (utils/call-ajax-orch "/configurations"
       {:on-success (fn [{:keys [parsed-response]}]
                      (swap! state assoc :loaded-import-confs? true :method-confs parsed-response))
        :on-failure (fn [{:keys [status-text]}]
                      (swap! state assoc :error-message status-text))
        :mock-data (create-mock-methodconfs-import)}))})


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


(defn render-import-overlay [workspace-id on-close on-import]
  [:div {:style modal-import-background
         :onKeyDown (common/create-key-handler [:esc] on-close)}
   [:div {:style modal-import-content}
    [:div {:style {:position "absolute" :right 2 :top 2}}
     [:div {:style {:backgroundColor (:button-blue style/colors) :color "#fff"
                    :padding "0.5em" :cursor "pointer"}
            :onClick #(on-close)}
      (icons/font-icon {:style {:fontSize "60%"}} :x)]]
    [:div {:style {:backgroundColor "#fff"
                   :borderBottom (str "1px solid " (:line-gray style/colors))
                   :padding "20px 48px 18px"}}
     [:div {:style {:fontSize 24 :align "center" :textAlign "center" :paddingBottom "0.5em"}}
      "Select A Method Configuration For Import"]
     [ImportWorkspaceMethodsConfigurationsList {:workspace-id workspace-id
                                                :on-close on-close
                                                :on-import on-import}]
     [:div {:style {:paddingTop "0.5em"}}]]]])
