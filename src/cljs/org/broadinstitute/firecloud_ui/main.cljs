(ns org.broadinstitute.firecloud-ui.main
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.page.method-repo :as method-repo]
   [org.broadinstitute.firecloud-ui.page.workspaces :as workspaces]
   [org.broadinstitute.firecloud-ui.session :as session]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(defn- logo []
  [:img {:src "assets/broad_logo.png" :style {:height 36}}])

(defn- footer []
  (let [thisyear (.getFullYear (js/Date.))
        startyear 2015
        yeartext (if (= startyear thisyear) (str startyear) (str startyear "-" thisyear))]
    [:div {:style {:padding "1em 0 1em 1ex" :fontSize "70%"}}
     (str "\u00A9 " yeartext " Broad Institute")]))

(react/defc TopNavBarLink
  {:render
   (fn [{:keys [props state]}]
     [:a {:href "javascript:;"
          :style {:padding "1ex" :textDecoration "none"
                  :fontWeight (when (:selected props) "bold")
                  :color (if (:hovering? @state) (:link-blue style/colors) "black") }
          :onClick (fn [e] ((:onClick props) e))
          :onMouseOver (fn [e] (swap! state assoc :hovering? true))
          :onMouseOut (fn [e] (swap! state assoc :hovering? false))}
      (:name props)])})


(react/defc TopNavBar
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:textAlign "right"}}
      [TopNavBarLink {:name "Workspaces"
                      :selected (= (:selected-item props) :workspaces)
                      :onClick (fn [e] ((:on-nav props) :workspaces))}]
      [TopNavBarLink {:name "Method Repository"
                      :selected (= (:selected-item props) :methods)
                      :onClick (fn [e] ((:on-nav props) :methods))}]])})

;; Content to display when logged in via Google
(react/defc LoggedIn
  {:get-initial-state
   (fn []
     {:top-level-page :workspaces})
   :render
   (fn [{:keys [state]}]
     [:div {}
      ;; Leave the Google button on the page to avoid possible errors.
      ;; TODO: figure out a better way to avoid the errors.
      [:div {:className "g-signin2" :data-onsuccess "onSignIn" :style {:display "none"}}]
      [:div {:style {:padding "1em" :borderBottom (str "1px solid " (:line-gray style/colors))}}
       [:div {:style {:float "right" :fontSize "70%"}}
        [:span {:style {:marginRight "1ex" :color (:link-blue style/colors)}}
         (-> (session/get-current-user)
             (utils/call-external-object-method :getBasicProfile)
             (utils/call-external-object-method :getName))]
        [:a {:href "javascript:;" :onClick (fn [e] (session/log-out))} "Log-Out"]]
       (logo)
       [:div {}
        [TopNavBar {:selected-item (:top-level-page @state)
                    :on-nav (fn [item] (swap! state assoc :top-level-page item))}]]]
      (case (:top-level-page @state)
        :workspaces [workspaces/Page]
        :methods [method-repo/Page])])})

;; Content to display when logged out
(react/defc LoggedOut
  {:render
   (fn []
     [:div {}
      [:div {:style {:padding "50px 25px"}}
       [:div {:style {:marginBottom "2em"}} (logo)]
       [:div {:className "g-signin2" :data-onsuccess "onSignIn" :data-theme "dark"}]
       [:div {:style {:paddingTop "25px" :fontSize ".75em"}}
        [:p {:style {:fontWeight "bold"}} "Warning"]
        [:p {} "This is a U.S. Government computer system, which may be accessed and used only for authorized Government
          business by authorized personnel. Unauthorized access or use of this computer system may subject violators to
          criminal, civil, and/or administrative action."]
        [:p {} "All information on this computer system may be intercepted, recorded, read, copied, and disclosed by and
          to authorized personnel for official purposes, including criminal investigations. Such information includes
          sensitive data encrypted to comply with confidentiality and privacy requirements. Access or use of this computer
          system by any person, whether authorized or unauthorized, constitutes consent to these terms. There is no right
          of privacy in this system."]]]])})

(react/defc App
  {:handleSignIn ; called from index.html on successful Google sign-in
   (fn [{:keys [state]} google-user]
     (session/set-current-user google-user)
     (swap! state assoc :is-logged-in? true))
   :render
   (fn [{:keys [state]}]
     [:div {}
      (cond
        (:is-logged-in? @state) [LoggedIn]
        :else [LoggedOut])
      (footer)])
   :component-did-mount
   (fn [{:keys [state]}]
     (session/on-log-out (fn [] (swap! state assoc :is-logged-in? false))))})

(defn- render-without-init [element]
  (react/render (react/create-element App) element nil goog.DEBUG))


(defonce dev-element (atom nil))


(defn ^:export render [element]
  (when goog.DEBUG
    (reset! dev-element element))
  (render-without-init element))


(defn dev-reload [figwheel-data]
  (render-without-init @dev-element))
