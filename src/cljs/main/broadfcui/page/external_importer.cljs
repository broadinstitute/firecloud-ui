(ns broadfcui.page.external-importer
  (:require
   [dmohs.react :as react]
   [broadfcui.common.style :as style]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))

(defn render-logged-out-import-tutorial [sign-in-handler]
  [:div {}
   [:h1 {:style {:marginBottom 0}} "Import Docker Image to FireCloud"]
   "In order to import data into FireCloud, you'll need two things:"
   [:div {:style {:display "flex" :justifyContent "space-between" :marginTop "1rem"}}
    [:div {}
     [:div {:style {:display "inline-block"
                    :background-color (:button-primary style/colors) :color "white"
                    :fontSize "1.25rem" :textAlign "center" :lineHeight "2rem"
                    :height "2rem" :width "2rem" :borderRadius "50%"}}
      "1"]
     [:h2 {:style {:display "inline" :marginLeft "0.5rem"}} "FireCloud Account"]]
    [:div {}
     [:div {:style {:display "inline-block"
                    :background-color (:button-primary style/colors) :color "white"
                    :fontSize "1.25rem" :textAlign "center" :lineHeight "2rem"
                    :height "2rem" :width "2rem" :borderRadius "50%"}}
      "2"]
     [:div {:style {:display "inline-block" :verticalAlign "top" :marginLeft "0.5rem"}}
      [:h2 {:style {:margin 0}} "Google Billing Project"]
      "To pay any storage or compute costs."]]]
   [:div {:style {:marginTop "2rem" :borderTop style/dark-line :display "flex"}}
    [:div {:style {:borderRight style/dark-line :paddingRight "1rem" :flexBasis "50%"}}
     [:h4 {} "Need to create a FireCloud Account?"]
     [:p {}
      "New user? FireCloud requires a Google account. Please use the \"Sign In\" button below to
      sign-in with your Google Account. Once you have successfully signed-in with Google, you will
      be taken to the FireCloud registration page."]
     [:div {:className "g-signin2" :data-theme "dark" :data-longtitle true :onClick sign-in-handler}]]
    [:div {:style {:paddingLeft "1rem"}}
     [:h4 {} "Already have a FireCloud account?"]
     [:div {:className "g-signin2" :data-theme "light" :onClick sign-in-handler}]]]])

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
