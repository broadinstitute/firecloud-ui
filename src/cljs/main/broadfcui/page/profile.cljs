(ns broadfcui.page.profile
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as components]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.input :as input]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   [broadfcui.utils.user :as user]
   ))

(defn build-identity-table [& pairs]
  [:table {:style {:borderSpacing "0 1rem" :marginTop "-1rem"}}
   [:tbody {}
    (map (fn [[k v]]
           [:tr {}
            [:td {:style {:width "12rem" :verticalAlign "top"}} k ":"]
            [:td {} v]])
         pairs)]])

(defn get-nih-link-href []
  (str (get @config/config "shibbolethUrlRoot")
       "/link-nih-account?redirect-url="
       (js/encodeURIComponent
        (let [loc (.-location js/window)]
          (str (.-protocol loc) "//" (.-host loc) "/#profile/nih-username-token={token}")))))

(defn get-param [param-name]
  (.get (js/URLSearchParams. js/window.location.search) param-name))

(defn build-state [state-map]
      ; TODO: BASE64 encode the state
      ; (encode (.getBytes (utils/->json-string state-map))))
  (:provider state-map))

(defn get-fence-oauth-href [oauth_host client_id provider]
  (str oauth_host
       "/user/oauth2/authorize?response_type=code&client_id="
       client_id
       "&scope=openid+google_credentials&redirect_uri="
       (js/encodeURIComponent
        (let [loc js/window.location]
          (str (.-protocol loc) "//" (.-host loc) "/#fence-callback")))
       "&state=" (build-state {:provider provider})))

(react/defc- NihLink
  {:render
   (fn [{:keys [state]}]
     (let [status (:nih-status @state)
           username (:linkedNihUsername status)
           expire-time (* (:linkExpireTime status) 1000)
           expired? (< expire-time (.now js/Date))
           expiring-soon? (< expire-time (utils/_24-hours-from-now-ms))
           datasets (:datasetPermissions status)]
       [:div {}
        [:h4 {} "NIH Account"
         (dropdown/render-info-box
          {:text
           (str "Linking with eRA Commons will allow FireCloud to automatically determine if you can access "
                "controlled datasets hosted in FireCloud (ex. TCGA) based on your valid dbGaP applications.")})]
        (cond
          (:error-message @state) (style/create-server-error-message (:error-message @state))
          (:pending-nih-username-token @state)
          (spinner {:ref "pending-spinner"} "Linking NIH account...")
          (nil? username)
          (links/create-external {:href (get-nih-link-href)} "Log-In to NIH to link your account")
          :else
          (apply build-identity-table
           ["Username" username]
           ["Link Expiration" [:div {:style {:flex "0 0 auto"}}
                               (if expired?
                                 [:span {:style {:color "red"}} "Expired"]
                                 [:span {:style {:color (when expiring-soon? "red")}} (common/format-date expire-time)])
                               [:div {}
                                (links/create-external {:href (get-nih-link-href)} "Log-In to NIH to re-link your account")]]]
           (map
            (fn [whitelist]
              [(str (:name whitelist) " Authorization") [:div {:style {:flex "0 0 auto"}}
                                                          (if (:authorized whitelist)
                                                            [:span {:style {:color (:state-success style/colors)}} "Authorized"]
                                                            [:span {:style {:color (:text-light style/colors)}}
                                                             "Not Authorized"
                                                             (dropdown/render-info-box
                                                              {:text
                                                               [:div {}
                                                                "Your account was linked, but you are not authorized to view this controlled dataset. Please go "
                                                                (links/create-external {:href "https://dbgap.ncbi.nlm.nih.gov/aa/wga.cgi?page=login"} "here")
                                                                " to check your credentials."]})])]])
            datasets)))]))
   :component-did-mount
   (fn [{:keys [this props state after-update]}]
     (let [{:keys [nih-token]} props]
       (if-not (nil? nih-token)
         (do
           (swap! state assoc :pending-nih-username-token nih-token)
           (after-update #(this :link-nih-account nih-token))
           ;; Navigate to the parent (this page without the token), but replace the location so
           ;; the back button doesn't take the user back to the token.
           (.replace (.-location js/window) (nav/get-link :profile)))
         (this :load-nih-status))))
   :component-did-update
   (fn [{:keys [refs]}]
     (when (@refs "pending-spinner")
       (common/scroll-to-center (react/find-dom-node (@refs "pending-spinner")))))
   :load-nih-status
   (fn [{:keys [state]}]
     (endpoints/profile-get-nih-status
      (fn [{:keys [success? status-code status-text get-parsed-response]}]
        (cond
          success? (swap! state assoc :nih-status (get-parsed-response))
          (= status-code 404) (swap! state assoc :nih-status :none)
          :else
          (swap! state assoc :error-message status-text)))))
   :link-nih-account
   (fn [{:keys [state]} token]
     (endpoints/profile-link-nih-account
      token
      (fn [{:keys [success? get-parsed-response]}]
        (if success?
          (do (swap! state dissoc :pending-nih-username-token :nih-status)
            (swap! state assoc :nih-status (get-parsed-response)))
          (swap! state assoc :error-message "Failed to link NIH account")))))})

(react/defc- DcfFenceLink
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [dcf-fence-status dcf-error-message pending-dcf-fence-token]} @state
           dcf-date-issued (.getTime (js/Date. (:issued_at dcf-fence-status)))
           dcf-expire-time (utils/_30-days-from-date-ms dcf-date-issued)
           dcf-expired? (< dcf-expire-time (.now js/Date))
           dcf-username (:username dcf-fence-status)]
       [:div {}
        [:h4 {} "DCF Framework Services by University of Chicago"]
        (cond
         dcf-error-message
         (style/create-server-error-message dcf-error-message)
         pending-dcf-fence-token
         (spinner {:ref "pending-spinner"} "Linking Framework Services account...")
         (nil? dcf-username)
         (links/create-external {:href (get-fence-oauth-href (config/dcf-fence-url) (config/dcf-fence-client-id) "dcf-fence") :target "_self"}
                                "Log-In to Framework Services to link your account")
         :else
         (build-identity-table
          ["Username" dcf-username]
          ["Link Expiration" [:div {}
                              (if dcf-expired?
                                [:span {:style {:color "red"}} "Expired"]
                                [:span {:style {:color (:state-success style/colors)}} (common/format-date dcf-expire-time)])
                              [:div {}
                               (links/create-external {:href (get-fence-oauth-href (config/dcf-fence-url) (config/dcf-fence-client-id) "dcf-fence") :target "_self" :style {:white-space "nowrap"}}
                                                      "Log-In to Framework Services to re-link your account")]]]))]))
   :component-did-mount
   (fn [{:keys [this locals state after-update]}]
     (if (= "dcf-fence" (get-param "state"))
       (let [dcf-fence-token (get-param "code")]
         (if (not-empty dcf-fence-token)
           (do
             (swap! state assoc :pending-dcf-fence-token dcf-fence-token)
             (after-update #(this :link-dcf-fence-account dcf-fence-token))
             ;; Navigate to the parent (this page without the token), but replace the location so
             ;; the back button doesn't take the user back to the token.
             (js/window.history.replaceState #{} "" (str "/#" (nav/get-path :profile)))))))
     (this :load-fence-status))
   :component-did-update
   (fn [{:keys [refs]}]
     (when (@refs "pending-spinner")
           (common/scroll-to-center (react/find-dom-node (@refs "pending-spinner")))))
   :load-fence-status
   (fn [{:keys [state]}]
     (endpoints/profile-get-fence-status
      "dcf-fence"
      (fn [{:keys [success? status-code status-text get-parsed-response]}]
        (cond
         success? (swap! state assoc :dcf-fence-status (get-parsed-response))
         (= status-code 404) (swap! state assoc :dcf-fence-status :none)
         :else
         (swap! state assoc :dcf-error-message status-text)))))
   :link-dcf-fence-account
   (fn [{:keys [state]} token]
     (endpoints/profile-link-fence-account
      "dcf-fence"
      token
      (js/encodeURIComponent
       (let [loc js/window.location]
         (str (.-protocol loc) "//" (.-host loc) "/#fence-callback")))
      (fn [{:keys [success? get-parsed-response]}]
        (if success?
          (do (swap! state dissoc :pending-dcf-fence-token :dcf-fence-status)
            (swap! state assoc :dcf-fence-status (get-parsed-response)))
          (swap! state assoc :dcf-error-message "Failed to link Framework Services account")))))})

(react/defc- DcpFenceLink
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [fence-status error-message pending-dcp-fence-token]} @state
           date-issued (.getTime (js/Date. (:issued_at fence-status)))
           expire-time (utils/_30-days-from-date-ms date-issued)
           expired? (< expire-time (.now js/Date))
           username (:username fence-status)]
       [:div {}
        [:h4 {} "DCP Framework Services by University of Chicago"]
        (cond
          error-message
          (style/create-server-error-message error-message)
          pending-dcp-fence-token
          (spinner {:ref "pending-spinner"} "Linking Framework Services account...")
          (nil? username)
          (links/create-external {:href (get-fence-oauth-href (config/dcp-fence-url) (config/dcp-fence-client-id) "fence") :target "_self"}
                                 "Log-In to Framework Services to link your account")
          :else
          (build-identity-table
           ["Username" username]
           ["Link Expiration" [:div {}
                               (if expired?
                                 [:span {:style {:color "red"}} "Expired"]
                                 [:span {:style {:color (:state-success style/colors)}} (common/format-date expire-time)])
                               [:div {}
                                (links/create-external {:href (get-fence-oauth-href (config/dcp-fence-url) (config/dcp-fence-client-id) "fence") :target "_self" :style {:white-space "nowrap"}}
                                                       "Log-In to Framework Services to re-link your account")]]]))]))
   :component-did-mount
   (fn [{:keys [this locals state after-update]}]
     (if (= "fence" (get-param "state"))
       (let [fence-token (get-param "code")]
         (if (not-empty fence-token)
           (do
             (swap! state assoc :pending-dcp-fence-token fence-token)
             (after-update #(this :link-dcp-fence-account fence-token))
             ;; Navigate to the parent (this page without the token), but replace the location so
             ;; the back button doesn't take the user back to the token.
             (js/window.history.replaceState #{} "" (str "/#" (nav/get-path :profile)))))))
     (this :load-fence-status))
   :component-did-update
   (fn [{:keys [refs]}]
     (when (@refs "pending-spinner")
       (common/scroll-to-center (react/find-dom-node (@refs "pending-spinner")))))
   :load-fence-status
   (fn [{:keys [state]}]
     (endpoints/profile-get-fence-status
      "fence"
      (fn [{:keys [success? status-code status-text get-parsed-response]}]
        (cond
          success? (swap! state assoc :fence-status (get-parsed-response))
          (= status-code 404) (swap! state assoc :fence-status :none)
          :else
          (swap! state assoc :error-message status-text)))))
   :link-dcp-fence-account
   (fn [{:keys [state]} token]
     (endpoints/profile-link-fence-account
      "fence"
      token
      (js/encodeURIComponent
       (let [loc js/window.location]
         (str (.-protocol loc) "//" (.-host loc) "/#fence-callback")))
      (fn [{:keys [success? get-parsed-response]}]
        (if success?
          (do (swap! state dissoc :pending-dcp-fence-token :fence-status)
            (swap! state assoc :fence-status (get-parsed-response)))
          (swap! state assoc :error-message "Failed to link Framework Services account")))))})


(react/defc- Form
  {:get-field-keys
   (fn []
     (list :firstName :lastName :title :contactEmail :institute :institutionalProgram :programLocationCity
           :programLocationState :programLocationCountry :pi))
   :get-values
   (fn [{:keys [state]}]
     (reduce-kv (fn [r k v] (assoc r k (string/trim v))) {} (merge @user/profile (:values @state))))
   :validation-errors
   (fn [{:keys [refs this]}]
     (apply input/validate refs (map name (this :get-field-keys))))
   :render
   (fn [{:keys [this props state]}]
     (cond (:error-message @state) (style/create-server-error-message (:error-message @state))
           @user/profile
           [:div {}
            [:h3 {:style {:marginBottom "0.5rem"}} "User Info"]
            (flex/box {}
                      (this :render-field :firstName "First Name")
                      (this :render-field :lastName "Last Name"))
            (this :render-field :title "Title")
            (this :render-field :contactEmail "Contact Email for Notifications (if different)" :optional :email)
            (flex/box {}
                      (this :render-field :institute "Institute")
                      (this :render-field :institutionalProgram "Institutional Program"))
            (when-not (:new-registration? props)
              [:div {:style {:clear "both" :margin "0.5em 0"}}
               [:div {:style {:marginTop "0.5em" :fontSize "88%"}}
                "Proxy Group"
                (dropdown/render-info-box
                 {:text
                  [:div {} "For more information about proxy groups, see the "
                   (links/create-external {:href "https://software.broadinstitute.org/firecloud/documentation/article?id=11185"} "user guide") "."]})]
               [:div {:data-test-id "proxyGroupEmail"
                      :style {:fontSize "88%" :padding "0.5em"}} (:userProxyGroupEmail @state)]])
            (common/clear-both)
            [:h3 {:style {:marginBottom "0.5rem"}} "Program Info"]
            (style/create-form-label "Non-Profit Status")
            (flex/box {:style {:fontSize "88%"}}
                      (this :render-radio-field :nonProfitStatus "Profit")
                      (this :render-radio-field :nonProfitStatus "Non-Profit"))
            (this :render-field :pi "Principal Investigator/Program Lead")
            (flex/box {}
                      (this :render-field :programLocationCity "City")
                      (this :render-field :programLocationState "State/Province")
                      (this :render-field :programLocationCountry "Country"))]
           :else (spinner "Loading User Profile...")))
   :render-radio-field
   (fn [{:keys [state]} key value]
     [:label {:style {:margin "0 1em 0.5em 0" :padding "0.5em 0"}}
      [:input {:type "radio" :value value :name key
               :checked (= value (get-in @state [:values key] (@user/profile key)))
               :onChange #(swap! state assoc-in [:values key] value)}]
      value])
   :render-field
   (fn [{:keys [state]} key label & flags]
     (let [flag-set (set flags)
           required? (not (flag-set :optional))
           email? (flag-set :email)]
       [:div {:style {:margin "0.5em 1em 0.5em 0"}}
        [:label {}
         (style/create-form-label label)
         [input/TextField {:style {:width 200}
                           :data-test-id key
                           :defaultValue (@user/profile key)
                           :ref (name key)
                           :placeholder (when email? (user/get-email))
                           :predicates [(when required? (input/nonempty label))
                                        (when email? (input/valid-email-or-empty label))]
                           :onChange #(swap! state assoc-in [:values key] (-> % .-target .-value))}]]]))
   :component-will-mount
   (fn [{:keys [state]}]
     (when-not @user/profile
       (user/reload-profile
        (fn [{:keys [success? status-text]}]
          (if success?
            (swap! state assoc :loaded-profile? true)
            (swap! state assoc :error-message status-text)))))
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/proxy-group (user/get-email))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (swap! state assoc :userProxyGroupEmail (get-parsed-response))))}))})


(react/defc- Page
  {:render
   (fn [{:keys [this props state]}]
     (let [new? (:new-registration? props)
           update? (:update-registration? props)]
       [:div {:style {:minHeight 300 :maxWidth 1250 :paddingTop "1.5rem" :margin "auto"}}
        [:h2 {} (cond new? "New User Registration"
                      update? "Update Registration"
                      :else "Profile")]
        [:div {:style {:display "flex"}}
         [:div {:style {:width "50%"}}
          [Form (merge {:ref "form"}
                       (select-keys props [:new-registration? :nih-token :fence-token]))]]
         [:div {:style {:width "50%"}}
          (when-not (:new-registration? props)
                    [:div {:style {:padding "1rem" :borderRadius 5 :backgroundColor (:background-light style/colors)}}
                     [:h3 {} "Identity & External Servers"]
                     [NihLink (select-keys props [:nih-token])]
                     [DcpFenceLink]
                     [DcfFenceLink]])]]
        [:div {:style {:marginTop "2em"}}
         (when (:server-error @state)
           [:div {:style {:marginBottom "1em"}}
            [components/ErrorViewer {:error (:server-error @state)}]])
         (when (:validation-errors @state)
           [:div {:style {:marginBottom "1em"}}
            (style/create-flexbox
             {}
             [:span {:style {:paddingRight "1ex"}}
              (icons/render-icon {:style {:color (:state-exception style/colors)}}
                                 :warning)]
             "Validation Errors:")
            [:ul {}
             (common/mapwrap :li (:validation-errors @state))]])
         (cond
           (:done? @state)
           [:div {:style {:color (:state-success style/colors)}} "Profile saved!"]
           (:in-progress? @state)
           (spinner "Saving...")
           :else
           [buttons/Button {:text (if new? "Register" "Save Profile")
                            :onClick #(this :save)}])]]))
   :save
   (fn [{:keys [props state refs]}]
     (utils/multi-swap! state (dissoc :server-error :validation-errors)
                        (assoc :in-progress? true))
     (let [values ((@refs "form") :get-values)
           validation-errors ((@refs "form") :validation-errors)]
       (cond
         (nil? validation-errors)
         (endpoints/profile-set
          values
          (fn [{:keys [success? get-parsed-response]}]
            (swap! state (fn [s]
                           (let [new-state (dissoc s :in-progress? :validation-errors)]
                             (if-not success?
                               (assoc new-state :server-error (get-parsed-response false))
                               (let [on-done (or (:on-done props) #(swap! state dissoc :done?))]
                                 (js/setTimeout on-done 2000)
                                 (user/reload-profile)
                                 (assoc new-state :done? true))))))))
         :else
         (utils/multi-swap! state (dissoc :in-progress? :done?)
                            (assoc :validation-errors validation-errors)))))})

(defn render [props]
  (react/create-element Page props))

(defn add-nav-paths []
  (nav/defpath
   :profile
   {:component Page
    :regex #"profile(?:/nih-username-token=([^\s/&]+))?"
    :make-props (fn [nih-token] (utils/restructure nih-token))
    :make-path (fn [] "profile")})
  (nav/defredirect
   {:regex #"fence-callback"
    :make-path (fn [] "profile")}))
