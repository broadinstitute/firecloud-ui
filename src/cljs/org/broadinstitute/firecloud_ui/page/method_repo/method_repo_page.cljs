(ns org.broadinstitute.firecloud-ui.page.method-repo.method-repo-page
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.modal :as modal]
   [org.broadinstitute.firecloud-ui.page.method-repo.method-config-importer :refer [MethodConfigImporter]]
   [org.broadinstitute.firecloud-ui.nav :as nav]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(react/defc Page
  {:render
   (fn [{:keys []}]
     [:div {:style {:padding "1em"}}
      [MethodConfigImporter
       {:allow-edit true
        :after-import
        (fn [{:keys [workspace-id config-id]}]
          (modal/push-modal
            {:header "Export successful"
             :content "Would you like to go to the edit page now?"
             :cancel-text "No, stay here"
             :ok-button
             {:text "Yes"
              :onClick modal/pop-modal
              :href (str "#workspaces/"
                         (:namespace workspace-id) "%3A" (:name workspace-id)
                         "/Method%20Configurations/"
                         (:namespace config-id) "%3A" (:name config-id))}}))}]])})
