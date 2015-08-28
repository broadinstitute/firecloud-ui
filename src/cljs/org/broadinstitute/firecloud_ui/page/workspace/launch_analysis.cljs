(ns org.broadinstitute.firecloud-ui.page.workspace.launch-analysis
  (:require
    [clojure.set :refer [union]]
    clojure.string
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.paths :as paths]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- render-launch-button [state refs entity workspace-id config on-success]
  (when-not (:launch-result @state)
          [:div {:style {:fontSize "106%" :lineHeight 1 :textAlign "center"}}
           [:div {:style {:padding "0.7em 0" :cursor "pointer"
                          :backgroundColor (:button-blue style/colors)
                          :color "#fff" :borderRadius 4
                          :border (str "1px solid " (:line-gray style/colors))}

                  ;; TODO: what should we show in the UI after submitting?
                  ;; TODO: don't enable submit button until an entity has been selected
                  ;; TODO: diable submit button after submitting
                  :onClick (fn [e]
                             (let [expression (clojure.string/trim (-> (@refs "expressionname") .getDOMNode .-value))
                                   payload (merge {:methodConfigurationNamespace (config "namespace")
                                                   :methodConfigurationName (config "name")
                                                   :entityType (entity "entityType")
                                                   :entityName (entity "name")}
                                                  (when-not (clojure.string/blank? expression) {:expression expression}))]
                               (utils/ajax-orch
                                (paths/submission-create workspace-id)
                                {:method :post
                                 :data (utils/->json-string payload)
                                 :headers{"Content-Type" "application/json"}
                                 :on-done (fn [{:keys [success? get-parsed-response xhr]}]
                                            ;; TODO total hack below for UI ...
                                            (swap! state assoc :launch-result (.-responseText xhr))
                                            (if-not success?
                                              (swap! state assoc :launch-exception true)
                                              (on-success (get-in (get-parsed-response) [0 "submissionId"]))))
                                 :canned-response {:responseText (utils/->json-string
                                                                  [{"workspaceName" {"namespace" "broad-dsde-dev",
                                                                                     "name" "alexb_test_submission"},
                                                                    "methodConfigurationNamespace" "my_test_configs",
                                                                    "submissionDate" "2015-08-18T150715.393Z",
                                                                    "methodConfigurationName" "test_config2",
                                                                    "submissionId" "62363984-7b85-4f27-b9c6-7577561f1326",
                                                                    "notstarted" [],
                                                                    "workflows" [{"messages" [],
                                                                                  "workspaceName" {"namespace" "broad-dsde-dev",
                                                                                                   "name" "alexb_test_submission"},
                                                                                  "statusLastChangedDate" "2015-08-18T150715.393Z",
                                                                                  "workflowEntity" {"entityType" "sample",
                                                                                                    "entityName" "sample_01"},
                                                                                  "status" "Submitted",
                                                                                  "workflowId" "70521329-88fe-4288-9325-2e6183e0a9dc"}],
                                                                    "status" "Submitted",
                                                                    "submissionEntity" {"entityType" "sample",
                                                                                        "entityName" "sample_01"},
                                                                    "submitter" "davidan@broadinstitute.org"}])
                                                   :status 200 :delay-ms (rand-int 1000)}})
                               ))} "Launch"]]))


(defn render-launch-overlay [entities state refs workspace-id config on-success]
  (let [entity-map (group-by #(% "entityType") entities)
        filter (or (:filter @state) "Sample")
        filtered-entities (entity-map filter)
        selected-entity (or (:selected-entity @state) (first filtered-entities))]
    [:div {:style {:padding "22px 48px 40px" :backgroundColor (:background-gray style/colors)}}
     (if (:launch-result @state)
       [:div {}
        (if (:launch-exception @state)
          (icons/font-icon {:style {:fontSize "200%" :color (:exception-red style/colors)}}
                           :status-warning-triangle)
          (icons/font-icon {:style {:fontSize "200%" :color (:success-green style/colors)}}
                           :status-done))
        [:pre {} (.stringify js/JSON (.parse js/JSON (:launch-result @state)) nil 2)]]
       [:div {}
        (style/create-form-label "Select Entity Type")
        (style/create-select
         {:style {:width "50%" :minWidth 50 :maxWidth 200} :ref "filter"
          :onChange #(let [value (-> (@refs "filter") .getDOMNode .-value)]
                       (swap! state assoc :filter value))}
         (keys entity-map))
        (style/create-form-label "Select Entity")
        [:div {:style {:backgroundColor "#fff" :border (str "1px solid " (:line-gray style/colors))
                       :padding "1em" :marginBottom "0.5em"}}
         (let [attribute-keys (apply union (map (fn [e] (set (keys (e "attributes")))) filtered-entities))]
           [table/Table
            {:key filter
             :empty-message "No entities available."
             :columns (concat
                       [{:header "" :starting-width 40 :resizable? false :reorderable? false
                         :content-renderer (fn [i data]
                                             [:input {:type "radio"
                                                      :checked (identical? data selected-entity)
                                                      :onChange #(swap! state assoc :selected-entity data)}])}
                        {:header "Entity Type" :starting-width 100 :sort-by :value}
                        {:header "Entity Name" :starting-width 100 :sort-by :value}]
                       (map (fn [k] {:header k :starting-width 100 :sort-by :value}) attribute-keys))
             :data (map (fn [m]
                          (concat
                           [m
                            (m "entityType")
                            (m "name")]
                           (map (fn [k] (get-in m ["attributes" k])) attribute-keys)))
                        filtered-entities)}])]
        (style/create-form-label "Define Expression")
        (style/create-text-field {:ref "expressionname" :placeholder "leave blank for default"})
        (render-launch-button state refs selected-entity workspace-id config on-success)])]))


(react/defc Page
  {:render
   (fn [{:keys [props state refs]}]
     [:div {:style {:backgroundColor "white"}}
      [:div {:style {:borderBottom (str "1px solid " (:line-gray style/colors))
                     :padding "20px 48px 18px"
                     :fontSize "137%" :fontWeight 400 :lineHeight 1}}
       "Select Entity"]
      [:div {:style {:position "absolute" :top 4 :right 4}}
       [comps/Button {:icon :x :onClick #((:on-cancel props))}]]
      [:div {:style {:marginTop "1em" :minHeight "10em"}}
       (let [{:keys [server-response]} @state
             {:keys [entities error-message]} server-response]
         (cond
           (nil? server-response)
           [:div {:style {:textAlign "center"}}
            [comps/Spinner {:text "Loading data..."}]]
           error-message (style/create-server-error-message error-message)
           (zero? (count @state))
           (style/create-message-well "No data found.")
           :else
           [:div {:style {:marginTop "-1em"}}
            (render-launch-overlay
             entities state refs (:workspace-id props) (:config-id props) (:on-success props))]))]])
   :component-did-mount
   (fn [{:keys [props state]}]
     (utils/ajax-orch
      (paths/get-entities-by-type (:workspace-id props))
      {:on-done (fn [{:keys [success? status-text get-parsed-response]}]
                  (swap! state assoc
                         :server-response (if success?
                                            {:entities (get-parsed-response)}
                                            {:error-message status-text})))
       :canned-response
       {:status 200
        :responseText (utils/->json-string
                       [{:name "Mock Sample" :entityType "Sample"}
                        {:name "Mock Participant" :entityType "Participant"}])
        :delay-ms 2000}}))})


(react/defc LaunchButton
  {:render
   (fn [{:keys [props state]}]
     [:span {}
      (when (:display-modal? @state)
        [comps/Dialog {:width "80%"
                       :dismiss-self #(swap! state dissoc :display-modal?)
                       :content (react/create-element
                                 Page
                                 (merge
                                  (select-keys props [:workspace-id :config-id :on-success])
                                  {:on-cancel #(swap! state dissoc :display-modal?)}))}])
      [comps/Button {:text "Launch Analysis..."
                     :onClick #(swap! state assoc :display-modal? true)}]])})


(defn render-button [workspace-id config-id on-success]
  (react/create-element
   LaunchButton {:workspace-id workspace-id :config-id config-id :on-success on-success}))
