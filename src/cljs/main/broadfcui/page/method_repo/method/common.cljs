(ns broadfcui.page.method-repo.method.common
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.links :as links]
   [broadfcui.common.method.config-io :as config-io]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
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


(defn render-config-table [{:keys [make-config-link-props configs style]}]
  [Table
   {:data configs
    :body {:empty-message "You don't have access to any published configurations for this method."
           :style (utils/deep-merge table-style/table-light
                                    {:table {:backgroundColor "white"}}
                                    style)
           :behavior {:reorderable-columns? false}
           :columns [{:header "Configuration" :initial-width 400
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
              :filter-bar {:inner {:width 300}}}}])


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


(defn render-config-details [{:keys [managers method payloadObject]}]
  [:div {}
   [:div {:style {:display "flex"}}
    (style/create-summary-block (str "Config Owner" (when (> (count managers) 1) "s"))
                                (string/join ", " managers))
    (style/create-summary-block "Designed For" (str "Method Snapshot " (:snapshotId method)))]

   (style/create-summary-block "Root Entity Type" (:rootEntityType payloadObject))

   (style/create-subsection-header "Connections")
   [IOView {:method-ref {:methodNamespace (:namespace method)
                         :methodName (:name method)
                         :methodVersion (:snapshotId method)}
            :values (select-keys payloadObject [:inputs :outputs])}]])
