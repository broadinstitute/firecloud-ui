(ns broadfcui.page.external-importer
  (:require
   [dmohs.react :as react]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.config :as config]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))

(defn render-logged-out-import-tutorial [sign-in-handler]
  (let [create-number-label
        (fn [number]
          [:div {:style {:display "inline-block"
                         :background-color (:button-primary style/colors) :color "white"
                         :fontSize "1.25rem" :textAlign "center" :lineHeight "2rem"
                         :height "2rem" :width "2rem" :borderRadius "50%"}}
           number])]
    [:div {}
     [:h1 {:style {:marginBottom "0.3rem"}} "Import Docker Image to FireCloud"]
     "In order to import data into FireCloud, you'll need two things:"
     [:div {:style {:marginTop "1.5rem"}}
      [:div {:style {:width "50%" :display "inline-block"}}
       (create-number-label 1)
       [:div {:style {:display "inline-block" :verticalAlign "top" :marginLeft "0.5rem"}}
        [:h2 {:style {:margin "0 0 0.3rem"}} "Google Billing Project"]
        "To pay any storage or compute costs."
        (links/create-external
         {:style {:display "block" :marginTop "0.3rem"}
          :href (config/billing-account-guide-url)}
         "Click here for instructions.")]]
      [:div {:style {:display "inline-block"}}
       (create-number-label 2)
       [:div {:style {:display "inline-block" :verticalAlign "top" :marginLeft "0.5rem"}}
        [:h2 {:style {:margin "0 0 0.3rem"}} "FireCloud Account"]
        "To maintain control over your data."]]]
     [:div {:style {:marginTop "2rem" :borderTop style/dark-line}}
      [:div {:style {:display "inline-block"
                     :borderRight style/dark-line :paddingRight "1rem"
                     :width "50%" :boxSizing "border-box"}}
       [:h3 {} "Need to create a FireCloud Account?"]
       [:p {:style {:lineHeight "130%"}}
        "New user? FireCloud requires a Google account. Please use the \"Sign In\" button below to
        sign-in with your Google Account. Once you have successfully signed-in with Google, you will
        be taken to the FireCloud registration page."]
       [:div {:style {:width 180}
              :className "g-signin2" :data-theme "dark" :data-longtitle true :data-height 40 :data-width 180
              :onClick sign-in-handler}]]
      [:div {:style {:display "inline-block" :verticalAlign "top" :paddingLeft "1rem"}}
       [:h3 {} "Already have a FireCloud account?"]
       [:div {:style {:width 120}
              :className "g-signin2" :data-theme "light" :data-height 40 :data-width 120
              :onClick sign-in-handler}]]]]))

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
