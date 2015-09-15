(ns org.broadinstitute.firecloud-ui.page.workspace.method-config-importer
  (:require
    [dmohs.react :as react]
    clojure.string
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common :as common :refer [clear-both]]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    ))

(defn- setrefcolor [refname refs color]
  (set! (-> (@refs refname) .getDOMNode .-style .-color) color))

(defn- create-formatted-label-text [label text]
  [:div {:style {:padding "10px 0"}}
   [:div {:style {:float "left" :width "180px"}} label ": "]
   text])

(defn- create-formatted-label-textfield [label textfield labelref ]
  [:div {}
   [:div {:ref labelref :style {  :float "left" :width "34ex" :paddingTop "10px"}} label ": "]
   textfield])

(defn- create-formatted-header [text]
  [:div {:style {:fontSize 24 :align "center" :textAlign "center" :paddingBottom "0.5em"}} text])

(defn- render-import-button [props refs state]
  (let [selected-config (:selected-method-config props)
        selected-conf-name (selected-config "name")
        selected-conf-namespace (selected-config "namespace")
        selected-conf-snapshot-id (selected-config "snapshotId")
        on-import (:on-import props)]
    [comps/Button
     {:text (if (nil? (:workspace-id props)) "Export" "Import")
      :onClick (fn []
                 (let [workspace-id
                       (if (nil? (:workspace-id props))
                         {:namespace (-> (@refs "destinationWSNamespace") .getDOMNode .-value)
                          :name (-> (@refs "destinationWSName") .getDOMNode .-value)}
                         (:workspace-id props))
                       dest-conf-name (-> (@refs "destinationName") .getDOMNode .-value)
                       dest-conf-namespace (-> (@refs "destinationNamespace") .getDOMNode .-value)
                       n-basic-valid (if (zero? (count (:name workspace-id))) false true)
                       ns-basic-valid (if (zero? (count (:namespace workspace-id))) false true)]
                   (if-not n-basic-valid
                     (setrefcolor "wnref" refs "red")
                     (if-not ns-basic-valid
                       (setrefcolor "wnsref" refs "red")
                       (do
                         (swap! state assoc :importing? true)
                         (endpoints/call-ajax-orch
                           {:endpoint (endpoints/copy-method-config-to-workspace workspace-id)
                            :headers {"Content-Type" "application/json"}
                            :payload {"configurationNamespace" selected-conf-namespace
                                      "configurationName" selected-conf-name
                                      "configurationSnapshotId" selected-conf-snapshot-id
                                      "destinationNamespace" dest-conf-namespace
                                      "destinationName" dest-conf-name}
                            :on-done (fn [{:keys [success? xhr]}]
                                       (swap! state assoc :importing? false)
                                       (if success?
                                         (on-import {"name" dest-conf-name
                                                     "namespace" dest-conf-namespace})
                                         (js/alert (str "Import Error: "
                                                     (.-responseText xhr)))))}))))))}]))


(react/defc ConfigurationImportForm
  {:render
   (fn [{:keys [props refs state]}]
     (let [selected-config (:selected-method-config props)
           selected-conf-name (selected-config "name")
           selected-conf-namespace (selected-config "namespace")
           selected-conf-snapshot-id (selected-config "snapshotId")
           workspace-id (:workspace-id props)
           on-import (:on-import props)]
       (assert selected-config (str "Missing a selected configuration: " props))
       (assert on-import (str "Missing on-import handler: " props))
       [:div {}
        [:div {:style {:position "absolute" :left 2 :top 2}}
         [comps/Button {:icon :angle-left
                        :onClick #((:on-back props))}]]
        (create-formatted-header
          (if (:workspace-id props)
            "Import Method Configuration"
            "Export Method Configuration To Workspace"))
        [:div {:style {:float "left"}}
         (for [[k v] {"Name" selected-conf-name
                      "Namespace" selected-conf-namespace
                      "Snapshot Id" selected-conf-snapshot-id}]
           (create-formatted-label-text k v ))]
        [:div {:style {:float "right"}}
         (create-formatted-label-textfield "Destination Name"
           (style/create-text-field {:defaultValue selected-conf-name
                                     :ref "destinationName"})"cnref")
         (create-formatted-label-textfield "Destination Namespace"
           (style/create-text-field {:onChange #(setrefcolor "wnref" refs "black")
                                     :defaultValue selected-conf-namespace
                                     :ref "destinationNamespace"})"cnsref")
         (when-not (:workspace-id props)
           [:div {}
            (create-formatted-label-textfield "Destination Workspace Name"
              (style/create-text-field {:onChange #(setrefcolor "wnref" refs "black")
                                        :defaultValue ""
                                        :ref "destinationWSName"})"wnref")
            (create-formatted-label-textfield "Destination Workspace Namespace"
              (style/create-text-field {:defaultValue "" :ref "destinationWSNamespace"})"wnsref")])]
        (clear-both)
        (render-import-button props refs state)
        (when (:importing? @state)
          [comps/Blocker {:banner (if (:workspace-id props)
                                    "Importing..." "Exporting...")}])]))})

(react/defc ConfigurationsTable
  {:render
   (fn [{:keys [props]}]
     (let [on-config-selected (:on-config-selected props)
           method-configs (:method-configs props)]
       (assert on-config-selected (str "Missing an on-selected-config handler: " props))
       (assert method-configs (str "Missing a list of method configs: " props))
       [:div {}
        (create-formatted-header
          (if (:selected-method props)
            (str "Browse Method Configurations for " (:selected-method props))
            "Select A Method Configuration For Import"))
        [table/Table
         {:empty-message "There are no method configurations available"
          :columns [{:header "Name" :starting-width 200 :filter-by #(% "name") :sort-by #(% "name")
                     :content-renderer
                     (fn [row-index conf]
                       [:a {:href "javascript:;"
                            :onClick #(on-config-selected conf)
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
        (:selected-method-config @state)
        [ConfigurationImportForm {:selected-method-config (:selected-method-config @state)
                                  :workspace-id (:workspace-id props)
                                  :on-back #(swap! state dissoc :selected-method-config)
                                  :on-import #((:on-import props) %)}]
        (:method-configs @state)
        [ConfigurationsTable {:selected-method (:selected-method props)
                              :method-configs (:method-configs @state)
                              :on-config-selected #(swap! state assoc :selected-method-config %)}]
        (:error-message @state) (style/create-server-error-message (:error-message @state))
        :else [comps/Spinner {:text
                              (if (:selected-method props)
                                (str "Loading configurations for " (:selected-method props))
                                "Loading configurations for import...")}])])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-configurations
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :method-configs (get-parsed-response))
                     (swap! state assoc :error-message status-text)))}))})

(defn render-import-overlay [workspace-id on-close on-import selected-method]
  (react/create-element
    [:div {}
     [:div {:style {:position "absolute" :right 2 :top 2}}
      [comps/Button {:icon :x :onClick #(on-close)}]]
     [:div {:style {:backgroundColor "#fff" :borderBottom (str "1px solid " (:line-gray style/colors))
                    :padding "20px 48px 18px"}}
      [ModalPage {:workspace-id workspace-id
                  :on-close on-close
                  :on-import on-import
                  :selected-method selected-method}]
      [:div {:style {:paddingTop "0.5em"}}]]]))
