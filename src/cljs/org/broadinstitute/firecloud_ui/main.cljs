(ns org.broadinstitute.firecloud-ui.main
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.session :as session]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))

(defn footer []
  (let [thisyear (.getFullYear (js/Date.))
        startyear 2015
        yeartext (if (= startyear thisyear) (str startyear) (str startyear "-" thisyear))]
    [:div {:style {:padding-top "1em" :font-size 12}} (str "\u00A9 " yeartext " Broad Institute")]))

;; Content to display when logged in via Google
(def LoggedIn
  (react/create-class
   {:render
    (fn []
      [:div {:style {:padding "50px 25px"}}
        ;; Leave the Google button on the page to avoid possible errors. TODO: figure out a better way to avoid the errors.
        [:div {:className "g-signin2" :data-onsuccess "onSignIn" :style {:display "none"}}]
        [:img {:src "http://www.textfiles.com/underconstruction/CoColosseumHoop5020underconstruction_blk.gif"}]
        [:br]
        [:img {:src "http://38.media.tumblr.com/319155ba5ef3d875466b9c07f18b0b8d/tumblr_mpsygdabiG1qzxh6go1_250.gif"}]
        [:br]
        [:div {:style {:fontSize "70%"}}
          [:span {:style {:fontWeight "bold" :marginRight "1ex"}}
           (-> (session/get-current-user)
             (utils/call-external-object-method :getBasicProfile)
             (utils/call-external-object-method :getName))]
          [:a {:href "javascript:;" :onClick (fn [e] (session/log-out))} "Log-Out"]]
       ])}))

;; Content to display when logged out
(def LoggedOut
  (react/create-class
   {:render
    (fn []
      [:div {:style {:padding "50px 25px"}}
        [:div {:className "g-signin2" :data-onsuccess "onSignIn" :data-theme "dark"}]])}))

(def App
  (react/create-class
   {:handleSignIn ; called from index.html on successful Google sign-in
        (fn [{:keys [state]} google-user]
          (session/set-current-user google-user)
          (swap! state assoc :is-logged-in? true))
    :render
    (fn [{:keys [state]}]
      [:div {:style {:padding "1em"}}
        [:div {} [:img {:src "assets/broad_logo.png" :style {:height 36}}]]

        (cond
          (:is-logged-in? @state) [LoggedIn]
          :else [LoggedOut]
        )

        (footer)])
    :component-did-mount
    (fn [{:keys [state]}]
      (session/on-log-out (fn [] (swap! state assoc :is-logged-in? false))))
    }))


(defn- render-without-init [element]
  (react/render (react/create-element App) element nil goog.DEBUG))


(defonce dev-element (atom nil))


(defn ^:export render [element]
  (when goog.DEBUG
    (reset! dev-element element))
  (render-without-init element))


(defn dev-reload [figwheel-data]
  (render-without-init @dev-element))
