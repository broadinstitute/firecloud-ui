(ns broadfcui.page.method-repo.method-repo-page
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.modal :as modal]
   [broadfcui.page.method-repo.method-config-importer :refer [MethodConfigImporter]]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))


(react/defc- Page
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:padding "1.5rem 1rem 0"}}
      [MethodConfigImporter
       (merge
        (select-keys props [:id :type])
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
              :href (nav/get-link :workspace-method-config workspace-id config-id)}}))})]])})

(defn add-nav-paths []
  (nav/defpath
   :method-repo
   {:component Page
    :regex #"methods"
    :make-props (fn [_] {})
    :make-path (fn [] "methods")})
  (nav/defpath
   :method
   {:component Page
    :regex #"methods/m/([^/]+)/([^/]+)/([^/]+)"
    :make-props (fn [namespace name snapshot-id]
                  {:type :method :id (utils/restructure namespace name snapshot-id)})
    :make-path (fn [method-id]
                 (str "methods/m/" (:namespace method-id) "/" (:name method-id) "/"
                      (:snapshot-id method-id)))})
  (nav/defpath
   :method-config
   {:component Page
    :regex #"methods/c/([^/]+)/([^/]+)/([^/]+)"
    :make-props (fn [namespace name snapshot-id]
                  {:type :config :id (utils/restructure namespace name snapshot-id)})
    :make-path (fn [config-id]
                 (str "methods/c/" (:namespace config-id) "/" (:name config-id) "/"
                      (:snapshot-id config-id)))}))
