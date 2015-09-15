(ns org.broadinstitute.firecloud-ui.page.method-config-importer
  (:require
    [dmohs.react :as react]
    [clojure.string :refer [trim]]
    [org.broadinstitute.firecloud-ui.common :as common :refer [clear-both get-text]]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    ))


(react/defc ConfigImportForm
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-workspace (first (:workspaces-list props))})
   :render
   (fn [{:keys [props state this]}]
     (cond
       (:loaded-config @state)
       (let [{:keys [workspace-id workspaces-list on-back]} props
             config (:loaded-config @state)]
         [:div {}
          (when (:importing? @state)
            [comps/Blocker {:banner "Importing..."}])
          (style/create-link #(on-back)
            (icons/font-icon {:style {:fontSize "70%" :marginRight "0.5em"}} :angle-left)
            "Back to table")
          [:div {:style {:fontSize 24 :align "center" :textAlign "center" :paddingBottom "0.5em"}}
           (if workspace-id "Import Method Configuration" "Export Method Configuration")]

          [:div {:style {:backgroundColor (:background-gray style/colors)
                         :borderRadius 8 :border (str "1px solid " (:line-gray style/colors))
                         :padding "1em"}}
           [:div {:style {:float "left" :width "33.33%" :textAlign "left"}}
            [:span {:style {:fontWeight 500 :padding "0 0.5em"}} "Method Namespace:"]
            [:span {} (get-in config ["method" "namespace"])]]
           [:div {:style {:float "left" :width "33.33%" :textAlign "center"}}
            [:span {:style {:fontWeight 500 :padding "0 0.5em"}} "Method Name:"]
            [:span {} (get-in config ["method" "name"])]]
           [:div {:style {:float "left" :width "33.33%" :textAlign "right"}}
            [:span {:style {:fontWeight 500 :padding "0 0.5em"}} "Method Version:"]
            [:span {} (get-in config ["method" "snapshotId"])]]
           (clear-both)]

          [:div {:style {:fontSize "120%" :margin "1.5em 0 0.5em 0"}} "Save as:"]
          [:div {:style {:float "left"}}
           (style/create-form-label "Configuration Namespace")
           (style/create-text-field {:defaultValue (config "namespace") :ref "namespace" :placeholder "Required"
                                     :onChange #(swap! state dissoc :bad-input)})]
          [:div {:style {:float "left" :marginLeft "1em"}}
           (style/create-form-label "Configuration Name")
           (style/create-text-field {:defaultValue (config "name") :ref "name" :placeholder "Required"
                                     :onChange #(swap! state dissoc :bad-input)})]
          (when (:bad-input @state)
            [:div {:style {:float "left" :padding "1.6em 0 0 1em"
                           :fontWeight 500 :color (:exception-red style/colors)}}
             "All fields required"])
          (clear-both)

          (when-not workspace-id
            [:div {:style {:marginBottom "1em"}}
             [:div {:style {:fontSize "120%" :margin "1.5em 0 1em 0"}} "Destination:"]
             [table/Table
              {:empty-message "No workspaces available"
               :columns [{:starting-width 35
                          :resizable? false :reorderable? false :filter-by :none :sort-by :none
                          :content-renderer
                          (fn [ws]
                            [:input {:type "radio"
                                     :checked (identical? ws (:selected-workspace @state))
                                     :onChange #(swap! state assoc :selected-workspace ws)}])}
                         {:header "Namespace" :starting-width 100}
                         {:header "Name" :starting-width 200}
                         {:header "Owner(s)" :starting-width 300}]
               :data (map
                       (fn [ws]
                         [ws
                          (get-in ws ["workspace" "namespace"])
                          (get-in ws ["workspace" "name"])
                          (ws "owners")])
                       workspaces-list)}]])

          [:div {:style {:textAlign "center"}}
           [comps/Button {:text (if workspace-id "Import" "Export")
                          :onClick #(react/call :perform-copy this)}]]])

       (:error @state) (style/create-server-error-message (:error @state))
       :else [comps/Spinner {:text "Loading configuration details..."}]))
   :perform-copy
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id config after-import on-back]} props
           [namespace name] (get-text refs "namespace" "name")
           workspace-id (or workspace-id
                          {:namespace (get-in (:selected-workspace @state) ["workspace" "namespace"])
                           :name (get-in (:selected-workspace @state) ["workspace" "name"])})]
       ;; TODO - implement generalized validation
       (if (some empty? [namespace name])
         (do
           (common/scroll-to-top)
           (swap! state assoc :bad-input true))
         (do
           (swap! state assoc :importing? true)
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/copy-method-config-to-workspace workspace-id)
              :payload {"configurationNamespace" (config "namespace")
                        "configurationName" (config "name")
                        "configurationSnapshotId" (config "snapshotId")
                        "destinationNamespace" namespace
                        "destinationName" name}
              :headers {"Content-Type" "application/json"}
              :on-done (fn [{:keys [success? xhr]}]
                         (swap! state dissoc :importing?)
                         (if success?
                           (do
                             (on-back)
                             (common/scroll-to-top)
                             (when after-import (after-import {"namespace" namespace
                                                               "name" name})))
                           (js/alert (str "Import error: " (.-responseText xhr)))))})))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-configuration
                    (get-in props [:config "namespace"])
                    (get-in props [:config "name"])
                    (get-in props [:config "snapshotId"]))
        :headers {"Content-Type" "application/json"}
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :loaded-config (get-parsed-response))
                     (swap! state assoc :error status-text)))}))})


(react/defc MethodImportForm
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-workspace (first (:workspaces-list props))})
   :render
   (fn [{:keys [props state this]}]
     (cond
       (:template @state)
       (let [{:keys [workspace-id workspaces-list on-back]} props
             template (:template @state)]
         [:div {}
          (when (:importing? @state)
            [comps/Blocker {:banner "Importing..."}])
          (style/create-link #(on-back)
            (icons/font-icon {:style {:fontSize "70%" :marginRight "0.5em"}} :angle-left)
            "Back to table")
          [:div {:style {:fontSize 24 :align "center" :textAlign "center" :paddingBottom "0.5em"}}
           (if workspace-id "Import Method Configuration" "Export Method Configuration")]

          [:div {:style {:backgroundColor (:background-gray style/colors)
                         :borderRadius 8 :border (str "1px solid " (:line-gray style/colors))
                         :padding "1em"}}
           [:div {:style {:float "left" :width "33.33%" :textAlign "left"}}
            [:span {:style {:fontWeight 500 :padding "0 0.5em"}} "Method Namespace:"]
            [:span {} (get-in template ["methodRepoMethod" "methodNamespace"])]]
           [:div {:style {:float "left" :width "33.33%" :textAlign "center"}}
            [:span {:style {:fontWeight 500 :padding "0 0.5em"}} "Method Name:"]
            [:span {} (get-in template ["methodRepoMethod" "methodName"])]]
           [:div {:style {:float "left" :width "33.33%" :textAlign "right"}}
            [:span {:style {:fontWeight 500 :padding "0 0.5em"}} "Method Version:"]
            [:span {} (get-in template ["methodRepoMethod" "methodVersion"])]]
           (clear-both)]

          [:div {:style {:fontSize "120%" :margin "1.5em 0 0.5em 0"}} "Save as:"]
          [:div {:style {:float "left"}}
           (style/create-form-label "Configuration Namespace")
           (style/create-text-field {:ref "namespace" :placeholder "Required"
                                     :onChange #(swap! state dissoc :bad-input)})]
          [:div {:style {:float "left" :marginLeft "1em"}}
           (style/create-form-label "Configuration Name")
           (style/create-text-field {:ref "name" :placeholder "Required"
                                     :onChange #(swap! state dissoc :bad-input)})]
          [:div {:style {:float "left" :marginLeft "1em"}}
           (style/create-form-label "Root Entity Type")
           (style/create-text-field {:ref "rootEntityType" :placeholder "Required"
                                     :onChange #(swap! state dissoc :bad-input)})]
          (when (:bad-input @state)
            [:div {:style {:float "left" :padding "1.6em 0 0 1em"
                           :fontWeight 500 :color (:exception-red style/colors)}}
             "All fields required"])
          (clear-both)

          (when-not workspace-id
            [:div {:style {:marginBottom "1em"}}
             [:div {:style {:fontSize "120%" :margin "1.5em 0 1em 0"}} "Destination:"]
             [table/Table
              {:empty-message "No workspaces available"
               :columns [{:starting-width 35
                          :resizable? false :reorderable? false :filter-by :none :sort-by :none
                          :content-renderer
                          (fn [ws]
                            [:input {:type "radio"
                                     :checked (identical? ws (:selected-workspace @state))
                                     :onChange #(swap! state assoc :selected-workspace ws)}])}
                         {:header "Namespace" :starting-width 100}
                         {:header "Name" :starting-width 200}
                         {:header "Owner(s)" :starting-width 300}]
               :data (map
                       (fn [ws]
                         [ws
                          (get-in ws ["workspace" "namespace"])
                          (get-in ws ["workspace" "name"])
                          (ws "owners")])
                       workspaces-list)}]])

          [:div {:style {:textAlign "center"}}
           [comps/Button {:text (if workspace-id "Import" "Export")
                          :onClick #(react/call :perform-copy this)}]]])

       (:error @state) (style/create-server-error-message (:error @state))
       :else [comps/Spinner {:text "Creating template..."}]))
   :perform-copy
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id after-import on-back]} props
           [namespace name rootEntityType] (get-text refs "namespace" "name" "rootEntityType")
           workspace-id (or workspace-id
                          {:namespace (get-in (:selected-workspace @state) ["workspace" "namespace"])
                           :name (get-in (:selected-workspace @state) ["workspace" "name"])})]
       ;; TODO - implement generalized validation
       (if (some empty? [namespace name rootEntityType])
         (do
           (common/scroll-to-top)
           (swap! state assoc :bad-input true))
         (do
           (swap! state assoc :importing? true)
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/post-workspace-method-config workspace-id)
              :payload (assoc (:template @state)
                         "namespace" namespace
                         "name" name
                         "rootEntityType" rootEntityType)
              :headers {"Content-Type" "application/json"}
              :on-done (fn [{:keys [success? xhr]}]
                         (swap! state dissoc :importing?)
                         (if success?
                           (do
                             (on-back)
                             (common/scroll-to-top)
                             (when after-import (after-import {"namespace" namespace
                                                               "name" name})))
                           (js/alert (str "Import error: " (.-responseText xhr)))))})))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/create-template (:method props))
        :payload (assoc (:method props)
                   "methodNamespace" (get-in props [:method "namespace"])
                   "methodName" (get-in props [:method "name"])
                   "methodVersion" (get-in props [:method "snapshotId"]))
        :headers {"Content-Type" "application/json"}
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :template (get-parsed-response))
                     (swap! state assoc :error status-text)))}))})


(defn- get-ordered-id-fields [method]
  (mapv #(method %) ["namespace" "name" "snapshotId"]))

(defn- format-date [date]
  (-> date js/moment (.format "LLL")))

(react/defc Table
  {:get-initial-state
   (fn []
     {:filter :all})
   :render
   (fn [{:keys [props state]}]
     (let [selected-items (case (:filter @state)
                            :all (concat (:methods props) (:configs props))
                            :methods-only (:methods props)
                            :configs-only (:configs props))
           build-button (fn [text f]
                          {:text text
                           :active? (= f (:filter @state))
                           :onClick #(swap! state assoc :filter f)})]
       [:div {}
        [:div {:style {:textAlign "center" :paddingBottom "1em"}}
         [comps/FilterButtons {:buttons [(build-button "All" :all)
                                         (build-button "Methods Only" :methods-only)
                                         (build-button "Configs Only" :configs-only)]}]]
        [table/Table
         {:key (gensym)
          :empty-message (case (:filter @state)
                           :methods-only "There are no methods available"
                           :configs-only "There are no method configurations available"
                           :all "There are no methods or configurations available")
          :columns [{:header "Type" :starting-width 100}
                    {:header "Namespace" :starting-width 150}
                    {:header "Name" :starting-width 200 :filter-by #(% "name") :sort-by #(% "name")
                     :content-renderer
                     (fn [item]
                       (style/create-link
                         (let [func (if (= "Method" (:type item)) :on-method-selected :on-config-selected)]
                           #((func props) (dissoc item :type)))
                         (item "name")))}
                    {:header "Snapshot ID" :starting-width 100}
                    {:header "Synopsis" :starting-width 160}
                    {:header "Created" :starting-width 210
                     :filter-by #(format-date %) :content-renderer #(format-date %)}
                    {:header "Referenced Method" :starting-width 250
                     :filter-by #(clojure.string/join " " (get-ordered-id-fields %))
                     :sort-by #(get-ordered-id-fields %)
                     :content-renderer (fn [i method]
                                         (when-not (empty? method)
                                           (clojure.string/join " | " (get-ordered-id-fields method))))}]
          :data (map
                  (fn [item]
                    [(:type item)
                     (item "namespace")
                     item
                     (item "snapshotId")
                     (item "synopsis")
                     (item "createDate")
                     (get item "method" {})])
                  selected-items)}]]))})


(react/defc MethodConfigImporter
  {:render
   (fn [{:keys [props state]}]
     (cond
       (:selected-method @state)
       [MethodImportForm {:method (:selected-method @state)
                          :workspace-id (:workspace-id props)
                          :workspaces-list (:workspaces-list @state)
                          :on-back #(swap! state dissoc :selected-method)
                          :after-import (:after-import props)}]
       (:selected-config @state)
       [ConfigImportForm {:config (:selected-config @state)
                          :workspace-id (:workspace-id props)
                          :workspaces-list (:workspaces-list @state)
                          :on-back #(swap! state dissoc :selected-config)
                          :after-import (:after-import props)}]

       (and (:configs-list @state) (:methods-list @state)
         (or (:workspace-id props) (:workspaces-list @state)))
       [Table {:configs (:configs-list @state)
               :methods (:methods-list @state)
               :on-config-selected #(swap! state assoc :selected-config %)
               :on-method-selected #(swap! state assoc :selected-method %)}]

       (:error-message @state) (style/create-server-error-message (:error-message @state))
       :else [comps/Spinner {:text "Loading..."}]))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-configurations
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :configs-list (map #(assoc % :type "Configuration") (get-parsed-response)))
                     (swap! state assoc :error-message status-text)))})
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-methods
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :methods-list (map #(assoc % :type "Method") (get-parsed-response)))
                     (swap! state assoc :error-message status-text)))})
     (when-not (:workspace-id props)
       (endpoints/call-ajax-orch
         {:endpoint endpoints/list-workspaces
          :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                     (if success?
                       (swap! state assoc :workspaces-list
                         (filter #(contains? #{"OWNER" "WRITER"} (% "accessLevel")) (get-parsed-response)))
                       (swap! state assoc :error-message status-text)))})))})
