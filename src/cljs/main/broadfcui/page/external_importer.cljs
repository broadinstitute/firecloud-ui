(ns broadfcui.page.external-importer
  (:require
   [dmohs.react :as react]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.config :as config]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))

(def import-title "Import Workflow to FireCloud")
(def import-subtitle "In order to import a workflow into FireCloud, you'll need two things:")

(defn render-import-tutorial []
  (let [create-number-label
        (fn [number]
          [:div {:style {:flexShrink 0 :height "2rem" :width "2rem" :borderRadius "50%"
                         :background-color (:button-primary style/colors) :color "white"
                         :fontSize "1.25rem" :textAlign "center" :lineHeight "2rem"}}
           number])]
    [:div {:style {:marginTop "1.5rem"}}
     [:div {:style {:display "flex"}}
      (create-number-label 1)
      [:div {:style {:margin "0 0.5rem"}}
       [:h2 {:style {:margin "0 0 0.3rem"}} "Google Billing Project"]
       "To pay any storage or compute costs"
       [:p {:style {:fontSize "80%"}}
        "Don’t know if you have one? If you own or have 'write & compute' access to an existing workspace, that means you have access to a billing project and you’re all set."
        (links/create-external {:style {:display "block" :marginTop "0.3rem"}
                                :href (config/billing-account-guide-url)}
          "Click here for instructions on how to set up a new billing project.")]]]
     [:div {:style {:display "flex"}}
      (create-number-label 2)
      [:div {:style {:marginLeft "0.5rem"}}
       [:h2 {:style {:margin "0 0 0.3rem"}} "FireCloud Account"]
       "To maintain control over your data."
       [:p {:style {:fontSize "80%"}}
        "Need to create a FireCloud account? FireCloud uses your Google account. Once you have signed in and completed the user profile registration step, you can start using FireCloud."
        (links/create-external {:style {:display "block" :marginTop "0.3rem"}
                                :href "https://software.broadinstitute.org/firecloud/documentation/article?id=9846"}
          "Learn how to create a Google account with any email address.")]]]]))

(react/defc Importer
  {:render
   (constantly nil)})

(defn add-nav-paths []
  (nav/defpath
   :import
   {:component Importer
    :regex #"import/([^/]+)/(.+)"
    :make-props (fn [source id]
                  (utils/restructure source id))
    :make-path (fn [source id]
                 (str source "/" id))}))