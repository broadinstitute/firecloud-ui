(ns broadfcui.page.method-repo.method-repo-page
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.modal :as modal]
   [broadfcui.page.method-repo.method-config-importer :refer [MethodConfigImporter]]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))


(react/defc Page
  {:render
   (fn [{:keys []}]
     [:div {:style {:padding "1em"}}
      [MethodConfigImporter
       {:allow-edit true
        :after-import
        (fn [{:keys [workspace-id config-id]}]
          (comps/push-ok-cancel-modal
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
