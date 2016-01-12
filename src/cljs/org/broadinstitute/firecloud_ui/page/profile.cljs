(ns org.broadinstitute.firecloud-ui.page.profile
  (:require
   clojure.string
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as components]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.config :as config]
   [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
   [org.broadinstitute.firecloud-ui.nav :as nav]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(defn get-nih-link-href []
  (str (get @config/config "shibbolethUrlRoot")
       "/link-nih-account?redirect-url="
       (js/encodeURIComponent
        (let [loc (.-location js/window)]
          (str (.-protocol loc) "//" (.-host loc) "/#profile/nih-username-token={token}")))))


(react/defc Form
  {:get-values
   (fn [{:keys [state]}]
     (reduce-kv (fn [r k v] (assoc r k (clojure.string/trim v))) {} (:values @state)))
   :render
   (fn [{:keys [state this]}]
     (cond (:error-message @state) (style/create-server-error-message (:error-message @state))
           (:values @state)
           [:div {}
            [:div {:style {:fontWeight "bold" :margin "1em 0 1em 0"}} "* - required fields"]
            (react/call :render-field this :firstName "*First Name:")
            (react/call :render-field this :lastName "*Last Name")
            (react/call :render-field this :title "*Title:")
            (react/call :render-field this :institute "*Institute:")
            (react/call :render-field this :institutionalProgram "*Institutional Program:")
            [:div {:style {:fontSize "88%"}} "Program Location:"
              [:div {}
                (react/call :render-nested-field this :programLocationCity "*City:")
                (react/call :render-nested-field this :programLocationState "*State/Province:")
                (react/call :render-nested-field this :programLocationCountry "*Country:")]]
                (react/call :render-field this :pi "*Principal Investigator/Program Lead:")
            [:div {:style {:marginBottom "1em" :fontSize "88%"}} "*NonProfit Status:"
              (react/call :render-radio-field this :nonProfitStatus "Profit")
              (react/call :render-radio-field this :nonProfitStatus "Non-Profit")]
            (react/call :render-field this :billingAccountName "Google Billing Account Name")
            [:div {} (react/call :render-nih-link-section this)]]
           :else [components/Spinner {:text "Loading User Profile..."}]))
   :render-radio-field
   (fn [{:keys [state]} key value]
       [:div {:style {:clear "both" :marginTop "0.167em" :width "30ex"}}
        (if (= (get-in @state [:values key]) value)
          [:input {:type "radio"
                   :value value
                   :checked true
                   :name key
                   :onChange #(swap! state assoc-in [:values key] value)} value]
          [:input {:type "radio"
                   :value value
                   :name key
                   :onChange #(swap! state assoc-in [:values key] value)} value])])
   :render-nested-field
   (fn [{:keys [state]} key label]
       [:div {:style {:float "left"}}
        [:label {}
         [:div {:style {:marginBottom "0.16667em"}} label]]
        (style/create-text-field
          {:style {:marginRight "1em" :marginTop "0.167em" :width "30ex"}
           :value (get-in @state [:values key])
           :onChange #(swap! state assoc-in [:values key] (-> % .-target .-value))})])
   :render-field
   (fn [{:keys [state]} key label]
     [:div {:style {:clear "both"}}
      [:label {}
       (style/create-form-label label)
       (style/create-text-field
         {:style {:marginTop "0.167em" :width "30ex"}
          :value (get-in @state [:values key])
          :onChange #(swap! state assoc-in [:values key] (-> % .-target .-value))})]])
   :render-nih-link-section
   (fn [{:keys [state]}]
     (let [{:keys [linkedNihUsername lastLinkTime isDbgapAuthorized]} (:values @state)
           isDbgapAuthorized (= isDbgapAuthorized "true")]
       [:div {}
        [:h3 {} "Linked NIH Account"]
        [:div {:style {:display "flex"}}
         [:div {:style {:flex "0 0 20ex"}} "NIH Username:"]
         [:div {:style {:flex "0 0 auto"}}
          (cond
            (:pending-nih-username-token @state)
            [components/Spinner {:ref "pending-spinner" :text "Creating NIH account link..."}]
            (nil? linkedNihUsername)
            [:a {:href (get-nih-link-href)}
             "Log-In to NIH to link your account"]
            :else
            linkedNihUsername)]]
        [:div {:style {:display "flex" :marginTop "1em"}}
         [:div {:style {:flex "0 0 20ex"}} "dbGaP Authorization:"]
         [:div {:style {:flex "0 0 auto"}}
          (if (nil? linkedNihUsername)
            [:i {} "N/A"]
            (if isDbgapAuthorized
              [:span {:style {:color (:success-green style/colors)}} "Authorized"]
              [:span {:style {:color (:text-gray style/colors)}} "Not Authorized"]))]]]))
   :component-did-update
   (fn [{:keys [prev-state state refs]}]
     (when (@refs "pending-spinner")
       (common/scroll-to-center (-> (@refs "pending-spinner") react/find-dom-node))))
   :component-did-mount
   (fn [{:keys [this props state refs after-update]}]
     (let [nav-context (nav/parse-segment (:parent-nav-context props))
           segment (:segment nav-context)]
       (if-not (clojure.string/blank? segment)
         (do
           (assert (re-find #"^nih-username-token=" segment) "Unexpected URL hash")
           (let [[_ token] (clojure.string/split segment #"=")]
             (swap! state assoc :pending-nih-username-token token)
             (after-update #(react/call :link-nih-account this token))
             ;; Navigate to the parent (this page without the token), but replace the location so
             ;; the back button doesn't take the user back to the token.
             (.replace (.-location js/window)
                       (str "#" (nav/create-hash (:parent-nav-context props))))))
         (react/call :load-profile this))))
   :load-profile
   (fn [{:keys [state]}]
     (endpoints/profile-get
      (fn [{:keys [success? status-text get-parsed-response]}]
        (if success?
          (let [parsed (get-parsed-response)]
            (swap! state assoc :values (common/parse-profile parsed)))
          (swap! state assoc :error-message status-text)))))
   :link-nih-account
   (fn [{:keys [this state]} token]
     (endpoints/profile-link-nih-account
      token
      (fn [{:keys [success?]}]
        (if success?
          (.. js/window -location (reload))
          (swap! state assoc :error-message "Failed to link NIH account")))))})


(react/defc Page
  {:render
   (fn [{:keys [this props state]}]
     (let [new? (:new-registration? props)]
       [:div {:style {:marginTop "2em"}}
        [:h2 {} (if new? "New User Registration" "Profile")]
        [:div {}
         [Form {:ref "form" :parent-nav-context (:nav-context props)}]]
        [:div {:style {:marginTop "2em"}}
         (cond
           (not (or (:in-progress? @state) (:done? @state)))
           [components/Button {:text (if new? "Register" "Save Profile") 
                               :onClick #(react/call :save this)}]
           (:done? @state)
           [:div {:style {:color (:success-green style/colors)}} "Profile saved!"]
           (:in-progress? @state)
           [components/Spinner {:text "Saving..."}]
           (:server-error @state)
           [components/ErrorViewer {:error (:server-error @state)}])]]))
   :save
   (fn [{:keys [this props state refs]}]
     (swap! state (fn [s] (assoc (dissoc s :server-error) :in-progress? true)))
     (let [values (react/call :get-values (@refs "form"))]
       (endpoints/profile-set
        values
        (fn [{:keys [success? get-parsed-response]}]
          (swap! state (fn [s]
                         (let [new-state (dissoc s :in-progress?)]
                           (if-not success?
                             (assoc new-state :server-error (get-parsed-response))
                             (let [on-done (or (:on-done props) #(swap! state dissoc :done?))]
                               (js/setTimeout on-done 2000)
                               (assoc new-state :done? true))))))))))})

(defn render [props]
  (react/create-element Page props))
