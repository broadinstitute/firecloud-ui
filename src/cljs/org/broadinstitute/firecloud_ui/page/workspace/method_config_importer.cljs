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
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

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
       :synopsis (str (rand-nth ["variant caller synopsis", "gene analyzer synopsis", "mutect synopsis"]) " " (inc i))
       :createDate (str "20" (inc i) "-06-10T16:54:26Z")
       :owner (rand-nth ["thibault@broadinstitute.org" "esalinas@broadinstitute.org"])})
    (range (rand-int 50))))

(defn- create-formatted-label-text [label text]
  [:div {:style {:padding "10px 0 10px 0"}}
   [:div {:style {:float "left" :width "180px"}} label ": "] [:div {} text]])

(defn- create-formatted-label-textfield [label textfield]
  [:div {}
   [:div {:style {:float "left" :width "180px" :paddingTop "10px"}} label ": "] [:div {} textfield]])

(defn- create-formatted-header [text]
  [:div {}
   [:div {:style {:fontSize 24 :align "center" :textAlign "center" :paddingBottom "0.5em"}} text]
   [:hr]])

(defn- render-import-button [props refs]
  (let [selected-config (:selected-method-config props)
        selected-conf-name (selected-config "name")
        selected-conf-namespace (selected-config "namespace")
        selected-conf-snapshot-id (selected-config "snapshotId")
        workspace-id (:workspace-id props)
        on-import (:on-import props)]
    [comps/Button
     {:text "Import"
      :onClick (fn []
                 (let [dest-conf-name (-> (@refs "destinationName") .getDOMNode .-value)
                       dest-conf-namespace (-> (@refs "destinationNamespace") .getDOMNode .-value)
                       post-data (create-post-data
                                   selected-conf-name
                                   selected-conf-namespace
                                   selected-conf-snapshot-id
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
                                   (on-import {"name" dest-conf-name "namespace" dest-conf-namespace})
                                   (js/alert (str "Import Error: " (.-responseText xhr)))))})))}]))


(react/defc ConfigurationImportForm
  {:render
   (fn [{:keys [props refs]}]
     (let [selected-config (:selected-method-config props)
           selected-conf-name (selected-config "name")
           selected-conf-namespace (selected-config "namespace")
           selected-conf-snapshot-id (selected-config "snapshotId")
           workspace-id (:workspace-id props)
           on-import (:on-import props)]
       (assert selected-config (str "Missing a selected configuration: " props))
       (assert on-import (str "Missing on-import handler: " props))
       (assert workspace-id (str "Missing workspace-id: " props))
       [:div {}
        (create-formatted-header "Import Method Configuration")
        (for [[k v] {"Name" selected-conf-name
                     "Namespace" selected-conf-namespace
                     "Snapshot Id" selected-conf-snapshot-id
                     }] (create-formatted-label-text k v))
        (for [[k v] {"Destination Name"
                     (style/create-text-field {:defaultValue selected-conf-name :ref "destinationName"})
                     "Destination Namespace"
                     (style/create-text-field {:defaultValue selected-conf-namespace :ref "destinationNamespace"})}]
          (create-formatted-label-textfield k v))
        (render-import-button props refs)
        [:span {:style {:marginLeft "0.5em"}}
         [comps/Button
          {:text "Back"
           :onClick #((:on-back props))}]]]))})


(react/defc ConfigurationsTable
  {:render
   (fn [{:keys [props]}]
     (let [on-config-selected (:on-config-selected props)
           method-configs (:method-configs props)]
       (assert on-config-selected (str "Missing an on-selected-config handler: " props))
       (assert method-configs (str "Missing a list of method configs: " props))
       [:div {}
        (create-formatted-header "Select A Method Configuration For Import")
        [table/Table
         {:empty-message "There are no method configurations available"
          :columns [{:header "Name" :starting-width 200 :filter-by #(% "name") :sort-by #(% "name")
                     :content-renderer
                     (fn [row-index conf]
                       [:a
                        {:onClick
                         (fn []
                           (on-config-selected conf))
                         :href "javascript:;"
                         :style {:color (:button-blue style/colors) :textDecoration "none"}}
                        (conf "name")])}
                    {:header "Namespace" :starting-width 200 :sort-by :value}
                    {:header "Snapshot Id" :starting-width 100 :sort-by :value}
                    {:header "Synopsis" :starting-width 160 :sort-by :value}
                    {:header "Create Date" :starting-width 210 :sort-by :value}
                    {:header "Owner" :starting-width 290 :sort-by :value}]
          :data (map
                  (fn [config]
                    [config
                     (config "namespace")
                     (config "snapshotId")
                     (config "synopsis")
                     (config "createDate")
                     (config "owner")])
                  method-configs)}]]))})


(react/defc ModalPage
  {:render
   (fn [{:keys [state props]}]
     [:div {}
      (cond
        (:show-import-mc-modal? @state)
        [ConfigurationImportForm {:selected-method-config (:selected-method-config @state)
                                  :workspace-id (:workspace-id props)
                                  :on-back (fn []
                                             (swap! state dissoc :show-import-mc-modal?
                                               :selected-method-config)
                                             (swap! state assoc :loaded-import-confs? true))
                                          :on-import (fn [& args]
                                                       (swap! state dissoc :show-import-mc-modal?)
                                                       (apply (:on-import props) args))}]
        (:loaded-import-confs? @state)
        [ConfigurationsTable {:method-configs (:method-configs @state)
                              :on-config-selected (fn [config]
                                                    (swap! state assoc
                                                      :selected-method-config config
                                                      :show-import-mc-modal? true
                                                      :loaded-import-confs? false))}]
        (:error-message @state) (style/create-server-error-message (:error-message @state))
        :else [comps/Spinner {:text "Loading configurations for import..."}])])
   :component-did-mount
   (fn [{:keys [state]}]
     (utils/call-ajax-orch "/configurations"
       {:on-success (fn [{:keys [parsed-response]}]
                      (swap! state assoc
                        :loaded-import-confs? true
                        :method-configs parsed-response))
        :on-failure (fn [{:keys [status-text]}]
                      (swap! state assoc :error-message status-text))
        :mock-data (create-mock-methodconfs-import)}))})

(defn render-import-overlay [workspace-id on-close on-import]
  (react/create-element
    [:div {}
     [:div {:style {:position "absolute" :right 2 :top 2}}
      [:div {:style {:backgroundColor (:button-blue style/colors) :color "#fff"
                     :padding "0.5em" :cursor "pointer"}
             :onClick #(on-close)}
       (icons/font-icon {:style {:fontSize "60%"}} :x)]]
     [:div {:style {:backgroundColor "#fff"
                    :borderBottom (str "1px solid " (:line-gray style/colors))
                    :padding "20px 48px 18px"}}
      [ModalPage {:workspace-id workspace-id :on-close on-close :on-import on-import}]
      [:div {:style {:paddingTop "0.5em"}}]]]))
