(ns org.broadinstitute.firecloud-ui.main
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
   [org.broadinstitute.firecloud-ui.nav :as nav]
   [org.broadinstitute.firecloud-ui.page.method-repo :as method-repo]
   [org.broadinstitute.firecloud-ui.page.profile :as profile-page]
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
   (fn [{:keys [this props state]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           page (keyword (:segment nav-context))]
       (when-not (or (contains? (set (map :key top-nav-bar-items)) page)
                     (= page :status))
         (nav/navigate (:nav-context props) "workspaces"))
       [:div {}
        [:div {:style {:padding "1em" :borderBottom (str "1px solid " (:line-gray style/colors))}} 
         (text-logo)
         (case (:registration-status @state)
           nil [:div {:style {:margin "2em 0" :textAlign "center"}}
                [comps/Spinner {:text "Loading user information..."}]]
           :error [:div {:style {:margin "2em 0"}}
                   (style/create-server-error-message (.-errorMessage this))]
           :not-registered (profile-page/render
                            {:new-registration? true
                             :on-done #(swap! state assoc :registration-status :registered)})
           :registered
           (case page
             :status (status-page/render) 
             [:div {}
              [TopNavBar {:selected-item page
                          :on-nav (fn [item] (nav/navigate (:nav-context props) (name item)))}]
              (let [item (first (filter #(= (% :key) page) top-nav-bar-items))]
                (if item
                  ((item :render) {:nav-context nav-context})
                  [:div {} "Page not found."]))]))]]))
   :component-did-mount
   (fn [{:keys [this state]}]
     (endpoints/profile-get
      :isRegistrationComplete
      (fn [{:keys [success? status-text get-parsed-response]}]
        (cond
          (and success? (= (get-in (get-parsed-response) ["keyValuePair" "value"]) "true"))
          (swap! state assoc :registration-status :registered)
          (not success?)
          (swap! state assoc :registration-status :not-registered)
          :else
          (do
            (set! (.-errorMessage this) status-text)
            (swap! state assoc :registration-status :error))))))})


(react/defc RegisterLink
  {:render
   (fn [{:keys [state]}]
     (if-not (:expanded? @state)
       [:a {:href "javascript:;" :onClick #(swap! state assoc :expanded? true)} "Register"]
       [:div {:style {:maxWidth 600}}
        [:div {} [:b {} "FireCloud requires a Google account."]]
        [:div {} "Please use the \"Sign In\" button above to sign-in with your Google Account."
         " Once you have successfully signed-in with Google, you will be taken to the FireCloud"
         " registration page."]]))})


(react/defc LoggedOut
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:padding "50px 25px"}}
      [:div {:style {:marginBottom "2em"}} (text-logo)]
      [:div {}
       [comps/Button {:text "Sign In"
                      :href (str "/service/login?path="
                                 (js/encodeURIComponent (js/decodeURIComponent (let [hash (nav/get-hash-value)]
                                                          (if (clojure.string/blank? hash)
                                                            "workspaces"
                                                            hash)))))}]]
      [:div {:style {:marginTop "1em"}} [RegisterLink]]
      [:div {:style {:maxWidth 600 :paddingTop "2em" :fontSize "small"}}
       [:p {:style {:fontWeight "bold"}} "WARNING NOTICE"]
       [:p {}
        "You are accessing a US Government web site which may contain information that must be "
        "protected under the US Privacy Act or other sensitive information and is intended for "
        "Government authorized use only."]
       [:p {}
        "Unauthorized attempts to upload information, change information, or use of this web site "
        "may result in disciplinary action, civil, and/or criminal penalties. Unauthorized users "
        "of this website should have no expectation of privacy regarding any communications or "
        "data processed by this website."]
       [:p {}
        "Anyone accessing this website expressly consents to monitoring of their actions and all "
        "communications or data transiting or stored on related to this website and is advised "
        "that if such monitoring reveals possible evidence of criminal activity, NIH may provide "
        "that evidence to law enforcement officials."]]])})


(react/defc App
  {:handle-hash-change
   (fn [{:keys [state]}]
     (swap! state assoc :root-nav-context (nav/create-nav-context)))
   :get-initial-state
   (fn []
     {:root-nav-context (nav/create-nav-context)})
   :render
   (fn [{:keys [state]}]
     [:div {}
      [:div {:style {:backgroundColor "white" :paddingBottom "2em"}}
       (if (:access-token @state)
         [LoggedIn {:nav-context (:root-nav-context @state)}]
         [LoggedOut])]
      (footer)])
   :component-did-mount
   (fn [{:keys [this state]}]
     (.addEventListener js/window "hashchange" (partial react/call :handle-hash-change this))
     (let [hash (-> js/window .-location .-hash)
           at-index (utils/str-index-of hash "?access_token=")
           [_ token] (clojure.string/split (subs hash at-index) #"=")]
       (when-not (neg? at-index)
         (swap! state assoc :access-token token)
         (.replace (.-location js/window) (subs hash 0 at-index)))))
   :component-will-update
   (fn [{:keys [next-state]}]
     (reset! utils/access-token (:access-token next-state)))})


(defn- render-without-init [element]
  (react/render (react/create-element App) element nil goog.DEBUG))


(defonce dev-element (atom nil))


(defn ^:export render [element]
  (when goog.DEBUG
    (reset! dev-element element))
  (render-without-init element))


(defn dev-reload [figwheel-data]
  (render-without-init @dev-element))
