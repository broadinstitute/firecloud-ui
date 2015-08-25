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


(defn render-launch-overlay [state refs workspace-id config]
  (when (:submitting? @state)
    [comps/ModalDialog
     {:width "80%"
      :content
      (react/create-element
       [:div {}
        [:div {:style {:backgroundColor "#fff"
                       :borderBottom (str "1px solid " (:line-gray style/colors))
                       :padding "20px 48px 18px"
                       :fontSize "137%" :fontWeight 400 :lineHeight 1}}
         "Select Entity"]
        [:div {:style {:position "absolute" :top 4 :right 4}}
         [comps/Button {:icon :x :onClick #(swap! state assoc :submitting? false
                                                  :launch-result nil :launch-exception nil)}]]
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
              :onChange #(let [value (-> (@refs "filter") .getDOMNode .-value)
                               entities (get-in @state [:entity-map value])]
                           (swap! state assoc :entities entities :selected-entity (first entities)))}
             (keys (:entity-map @state)))
            (style/create-form-label "Select Entity")
            (if (zero? (count (:entities @state)))
              (style/create-message-well "No entities to display.")
              [:div {:style {:backgroundColor "#fff" :border (str "1px solid " (:line-gray style/colors))
                             :padding "1em" :marginBottom "0.5em"}}
               (let [attribute-keys (apply union (map (fn [e] (set (keys (e "attributes")))) (:entities @state)))]
                 [table/Table
                  {:key (name (gensym))
                   :columns (concat
                             [{:header "" :starting-width 40 :resizable? false :reorderable? false
                               :content-renderer (fn [i data]
                                                   [:input {:type "radio"
                                                            :checked (identical? data (:selected-entity @state))
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
                              (:entities @state))}])])
            (style/create-form-label "Define Expression")
            (style/create-text-field {:ref "expressionname" :defaultValue "" :placeholder "leave blank for default"})])]

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
                                                   :entityType ((:selected-entity @state) "entityType")
                                                   :entityName ((:selected-entity @state) "name")}
                                                  (when-not (clojure.string/blank? expression) {:expression expression}))]
                               (utils/ajax-orch
                                (paths/submit-method-path workspace-id)
                                {:method :post
                                 :data (utils/->json-string payload)
                                 :headers{"Content-Type" "application/json"}
                                 :on-done (fn [{:keys [success? xhr]}]
                                            ;; TODO total hack below for UI ...
                                            (swap! state assoc :launch-result (.-responseText xhr))
                                            (if-not success?
                                              (swap! state assoc :launch-exception true)))
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
                               ))} "Launch"]])
        ])}]))
