(ns broadfcui.page.method-repo.method.common
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.method.config-io :as config-io]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.net :as net]
   [broadfcui.page.workspace.method-configs.synchronize :as mc-sync]
   [broadfcui.utils :as utils]
   ))


(react/defc- IOView
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [error inputs-outputs]} @state]
       (cond error [comps/ErrorViewer (:error error)]
             inputs-outputs [config-io/IOTables {:style {:marginTop "1rem"}
                                                 :inputs-outputs inputs-outputs
                                                 :values (:values props)
                                                 :default-hidden? (:default-hidden? props)}]
             :else [comps/Spinner {:text "Loading inputs/outputs..."}])))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint endpoints/get-inputs-outputs
       :payload (:method-ref props)
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (swap! state assoc :inputs-outputs (get-parsed-response))
                    (swap! state assoc :error (get-parsed-response false))))}))})


(react/defc ConfigTable
  {:refresh
   (fn [{:keys [props state]}]
     (swap! state dissoc :associated-configs :compatible-configs :resolved-configs :configs-error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-agora-method-configs (:method-id props))
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? parsed-response]}]
                   (if success?
                     (swap! state assoc :associated-configs parsed-response)
                     (swap! state assoc :configs-error (:message parsed-response)))))})
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-agora-compatible-configs (conj (:method-id props) (select-keys props [:snapshot-id])))
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? parsed-response]}]
                   (if success?
                     (swap! state assoc :compatible-configs (set parsed-response))
                     (swap! state assoc :configs-error (:message parsed-response)))))}))
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [style make-config-link-props]} props
           {:keys [resolved-configs configs-error]} @state]
       (cond configs-error
             [:div {:style {:textAlign "center" :color (:state-exception style/colors)}}
              "Error loading configs: " configs-error]

             (not resolved-configs)
             [:div {:style {:textAlign "center" :padding "1rem"}}
              [comps/Spinner {:text "Loading configs..."}]]

             :else
             [Table
              {:data resolved-configs
               :body {:empty-message "You don't have access to any published configurations for this method."
                      :style (utils/deep-merge table-style/table-light
                                               {:table {:backgroundColor "white"}}
                                               style)
                      :behavior {:reorderable-columns? false}
                      :columns [{:id "compatible?" :initial-width 30 :resizable? false :sortable? false
                                 :column-data :compatible?
                                 :as-text (fn [c] (if c "Compatible" "Incompatible"))
                                 :render (fn [c] (if c (icons/render-icon {:style {:color (:state-success style/colors)}} :done)
                                                       (icons/render-icon {:style {:color (:state-warning style/colors)}} :warning)))}
                                {:header "Configuration" :initial-width 400
                                 :as-text (fn [{:keys [name namespace snapshotId]}]
                                            (str namespace "/" name " snapshot " snapshotId))
                                 :sort-by #(replace % [:namespace :name :snapshotId])
                                 :render (fn [{:keys [name namespace snapshotId] :as config}]
                                           (links/create-internal
                                             (merge {:data-test-id (str namespace "-" name "-" snapshotId "-link")}
                                                    (make-config-link-props config))
                                             (style/render-name-id (str namespace "/" name) snapshotId)))}
                                {:header "Method Snapshot" :initial-width 135 :filterable? false
                                 :column-data #(get-in % [:payloadObject :methodRepoMethod :methodVersion])}
                                {:header "Synopsis" :initial-width :auto
                                 :column-data :synopsis}]}
               :toolbar {:style {:padding 2} ;; gives room for highlight around filter field
                         :filter-bar {:inner {:width 300}}}}])))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :refresh))
   :component-did-update
   (fn [{:keys [props prev-state state]}]
     (let [has-both? (fn [s] (and (:associated-configs s) (:compatible-configs s)))]
       (when (and (not (has-both? prev-state))
                  (has-both? @state))
         (let [resolved-configs (mapv (fn [config]
                                        (assoc config :compatible? (contains? (:compatible-configs @state) config)))
                                      (:associated-configs @state))
               {:keys [on-load]} props]
           (when on-load (on-load resolved-configs))
           (swap! state assoc :resolved-configs resolved-configs)))))})


(defn render-post-export-dialog [{:keys [workspace-id config-id dismiss]}]
  [modals/OKCancelForm
   {:header "Export successful"
    :content "Would you like to go to the edit page now?"
    :cancel-text "No, stay here"
    :dismiss dismiss
    :ok-button
    {:text "Yes"
     :onClick #(mc-sync/flag-synchronization)
     :href (nav/get-link :workspace-method-config workspace-id config-id)}}])


(defn render-config-details [{:keys [managers method payloadObject snapshotComment]}]
  [:div {}
   [:div {:style {:display "flex"}}
    (style/create-summary-block (str "Configuration Owner" (when (> (count managers) 1) "s"))
                                (string/join ", " managers))
    (style/create-summary-block "Designed For" (str "Method Snapshot " (:snapshotId method)))]
   [:div {:style {:display "flex"}}
    (style/create-summary-block "Root Entity Type" (:rootEntityType payloadObject))
    ;; Snapshot comments for configs can only be created by the API. Hide the comment field if it doesn't
    ;; exist to avoid tantalizing UI-only users with something they can't have (at least until GAWB-2702)
    (some->> snapshotComment (style/create-summary-block "Snapshot Comment"))]

   (style/create-subsection-header "Connections")
   [IOView {:method-ref {:methodNamespace (:namespace method)
                         :methodName (:name method)
                         :methodVersion (:snapshotId method)}
            :values (select-keys payloadObject [:inputs :outputs])}]])
