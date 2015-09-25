(ns org.broadinstitute.firecloud-ui.main
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.nav :as nav]
   [org.broadinstitute.firecloud-ui.page.method-repo :as method-repo]
   [org.broadinstitute.firecloud-ui.page.status :as status-page]
   [org.broadinstitute.firecloud-ui.page.workspaces-list :as workspaces]
   [org.broadinstitute.firecloud-ui.utils :as utils :refer [rlog jslog cljslog]]
   ))


(defn- logo []
  [:img {:src "assets/broad_logo.png" :style {:height 36}}])

; Temporary replacement for the Broad Logo.
(defn- text-logo []
  [:div {:style {:fontSize "32px" :color (:button-blue style/colors) :fontWeight "bold"}}
   "FireCloud"])


(react/defc PopUpFooterControl
  {:render
   (fn [{:keys [state]}]
     [:div {:style {:minWidth 50 :minHeight 20}
            :onMouseOver #(swap! state assoc :visible? true)
            :onMouseOut #(swap! state dissoc :visible?)}
      [:div {:style {:display (when-not (or (:visible? @state) (not @utils/use-live-data?)) "none")
                     :padding 20 :paddingBottom 10 :margin -20 :marginBottom -10}}
       [:div {}
        "Fake data: "
        [:a {:href "javascript:;"
             :style {:textDecoration "none" :color (if @utils/use-live-data? "green" "red")}
             :onClick #(do (swap! utils/use-live-data? not) (swap! state assoc :foo 1))}
         (if @utils/use-live-data? "off" "on")]]
       [:div {}
        [:a {:href "#status" :style {:textDecoration "none"}} "Status Page"]]]])})


(defn- footer []
  (let [thisyear (.getFullYear (js/Date.))
        startyear 2015
        yeartext (if (= startyear thisyear) (str startyear) (str startyear "-" thisyear))
        spacer [:span {:style {:padding "0 0.6em 0 0.5em"}} "|"]
        Link (react/create-class
              {:render
               (fn [{:keys [props state]}]
                 [:a {:href (:href props)
                      :target (:target props)
                      :style {:color (:footer-text style/colors)
                              :textDecoration (when-not (:hovering? @state) "none")}
                      :onMouseOver #(swap! state assoc :hovering? true)
                      :onMouseOut  #(swap! state assoc :hovering? false)}
                  (:text props)])})]
    [:div {:style {:borderTop (str "2px solid " (:line-gray style/colors))
                   :padding "1em 25px 2em 25px"
                   :color (:footer-text style/colors) :fontSize "90%"}}
     [:div {:style {:float "right"}} [PopUpFooterControl]]
     [:div {:style {:display "block"}}
      (str "\u00A9 " yeartext " Broad Institute")
      spacer
      [Link {:href "#" :text "Privacy Policy" :target "_self"}]
      spacer
      [Link {:href "#" :text "Terms of Service" :target "_self"}]
      spacer
      [Link {:href "mailto:firecloud-help@broadinstitute.org?Subject=FireCloud%20Help" :text "Support" :target "_blank"}]]]))


(def top-nav-bar-items
  [{:key :workspaces
    :name "Workspaces"
    :render #(react/create-element workspaces/Page %)}
   {:key :methods
    :name "Method Repository"
    :render #(react/create-element method-repo/Page %)}])


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
      (map (fn [item] [TopNavBarLink {:name (item :name)
                                      :selected (= (:selected-item props) (item :key))
                                      :onClick (fn [e] ((:on-nav props) (item :key)))}])
            top-nav-bar-items)])})


(react/defc LoggedIn
  {:render
   (fn [{:keys [props]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           page (keyword (:segment nav-context))]
       (when-not (or (contains? (set (map :key top-nav-bar-items)) page)
                     (= page :status))
         (nav/navigate (:nav-context props) "workspaces"))
       [:div {}
        [:div {:style {:padding "1em" :borderBottom (str "1px solid " (:line-gray style/colors))}}
         [:div {:style {:float "right" :fontSize "70%"}}
          [:span {:style {:marginRight "1ex" :color (:link-blue style/colors)}}
           (-> (utils/get-current-user)
               (utils/call-external-object-method :getBasicProfile)
               (utils/call-external-object-method :getName))]
          ;; Sadly, reloading the page seems to be the only reliable way to log out.
          [:a {:href "javascript:;" :onClick (fn [e] (-> js/window .-location (.reload)))}
           "Log-Out"]]
         (text-logo)
         (if (= page :status)
           (status-page/render)
           [:div {}
            [TopNavBar {:selected-item page
                        :on-nav (fn [item] (nav/navigate (:nav-context props) (name item)))}]
            (let [item (first (filter #(= (% :key) page) top-nav-bar-items))]
              (if item
                ((item :render) {:nav-context nav-context})
                [:div {} "Page not found."]))])]]))})


(react/defc LoggedOut
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:display (when (:hidden? props) "none")}}
      [:div {:style {:padding "50px 25px"}}
       [:div {:style {:marginBottom "2em"}} (text-logo)]
       [:div {:id "google-sign-in-button"}]
       [:div {:style {:width "600px" :paddingTop "25px" :fontSize ".75em"}}
        [:p {:style {:fontWeight "bold"}} "WARNING NOTICE"]
        [:p {}
         "You are accessing a US Government web site which may contain information that must be protected under the US"
         " Privacy Act or other sensitive information and is intended for Government authorized use only."]
        [:p {}
         "Unauthorized attempts to upload information, change information, or use of this web site may result in"
         " disciplinary action, civil, and/or criminal penalties. Unauthorized users of this website should have no"
         " expectation of privacy regarding any communications or data processed by this website."]
        [:p {}
         "Anyone accessing this website expressly consents to monitoring of their actions and all communications or"
         " data transiting or stored on related to this website and is advised that if such monitoring reveals possible"
         " evidence of criminal activity, NIH may provide that evidence to law enforcement officials."]]]])})


(def google-sign-in-scopes
  ["profile"
   "email"
   "https://www.googleapis.com/auth/devstorage.full_control"
   "https://www.googleapis.com/auth/compute"])


(react/defc App
  {:handleGapiDidLoad
   (fn [{:keys [state]}]
     (utils/call-external-object-method
      js/gapi :load "auth2"
      (fn []
        (let [google-auth (-> js/gapi (aget "auth2")
                              (utils/call-external-object-method
                               :init
                               (clj->js
                                {:client_id (aget js/window "GOOGLE_CLIENT_ID")
                                 :scope (clojure.string/join " " google-sign-in-scopes)})))]
          (utils/call-external-object-method
           (aget google-auth "isSignedIn")
           :listen (fn [signed-in?]
                     (swap! state assoc :signed-in? signed-in?)
                     ;; Also set cookie for easy Swagger use.
                     (let [expire-date (js/Date.)
                           auth-response (utils/call-external-object-method
                                          (utils/get-current-user) :getAuthResponse)]
                       ;; Set expiration for one hour from now.
                       (.setTime expire-date (+ (.getTime expire-date) (* 60 60 1000)))
                       (set! js/document.cookie
                             (str "access_token=" (aget auth-response "access_token")
                                  "; expires=" (.toGMTString expire-date) "; path=/"))))))))
     (utils/call-external-object-method
      js/gapi :load "signin2"
      (fn []
        (-> js/gapi (aget "signin2")
            (utils/call-external-object-method
             :render
             "google-sign-in-button"
             (clj->js
              {:theme "dark"}))))))
   :handle-hash-change
   (fn [{:keys [state]}]
     (swap! state assoc :root-nav-context (nav/create-nav-context)))
   :get-initial-state
   (fn []
     {:root-nav-context (nav/create-nav-context)})
   :render
   (fn [{:keys [state]}]
     [:div {}
      [:div {:style {:backgroundColor "white" :paddingBottom "2em"}}
       (when (:signed-in? @state) [LoggedIn {:nav-context (:root-nav-context @state)}])
        ;; This has to be hidden rather than simply omitted. After a successful login, Google
        ;; attempts to find the sign-in button and manipulate it, which throws an error if it is
        ;; not present on the page.
       [LoggedOut {:hidden? (:signed-in? @state)}]]
      (footer)])
   :component-did-mount
   (fn [{:keys [this state]}]
     (.addEventListener js/window "hashchange" (partial react/call :handle-hash-change this)))})


(defn- render-without-init [element]
  (react/render (react/create-element App) element nil goog.DEBUG))


(defonce dev-element (atom nil))


(defn ^:export render [element]
  (when goog.DEBUG
    (reset! dev-element element))
  (render-without-init element))


(defn dev-reload [figwheel-data]
  (render-without-init @dev-element))
