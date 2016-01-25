(ns org.broadinstitute.firecloud-ui.page.method-repo
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
   [org.broadinstitute.firecloud-ui.page.method-config-importer :refer [MethodConfigImporter]]
   [org.broadinstitute.firecloud-ui.nav :as nav]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(react/defc Page
  {:render
   (fn [{:keys [state]}]
     [:div {:style {:padding "1em"}}
      (when (:destination @state)
        [dialog/Dialog
         {:dismiss-self #(swap! state dissoc :destination)
          :width 500
          :content
          (react/create-element
            [dialog/OKCancelForm
             {:dismiss-self #(swap! state dissoc :destination)
              :header "Import successful"
              :content "Would you like to go to the edit page now?"
              :cancel-text "No, stay here"
              :ok-button [comps/Button {:text "Yes" :href (:destination @state)}]}])}])
      [MethodConfigImporter {:allow-edit true
                             :after-import (fn [{:keys [workspace-id config-id]}]
                                             (common/scroll-to-top 100)
                                             (swap! state assoc
                                               :destination (str "#workspaces/"
                                                              (:namespace workspace-id) "%3A" (:name workspace-id)
                                                              "/Method%20Configurations/"
                                                              (:namespace config-id) "%3A" (:name config-id))))}]])})
