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
   [broadfcui.user-info :as user-info]
   [broadfcui.utils :as utils]
   ))

(defn get-nih-link-href []
  (str (get @config/config "shibbolethUrlRoot")
       "/link-nih-account?redirect-url="
       (js/encodeURIComponent
        (let [loc (.-location js/window)]
          (str (.-protocol loc) "//" (.-host loc) "/#profile/nih-username-token={token}")))))

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
        [:h3 {} "Linked NIH Account"]
        (cond
          (:error-message @state) (style/create-server-error-message (:error-message @state))
          (:pending-nih-username-token @state)
          (spinner {:ref "pending-spinner"} "Linking NIH account...")
          (nil? username)
          (links/create-external {:href (get-nih-link-href)} "Log-In to NIH to link your account")
          :else
          [:div {}
           [:div {:style {:display "flex"}}
            [:div {:style {:flex "0 0 12rem"}} "eRA Commons / NIH Username:"]
            [:div {:style {:flex "0 0 auto"}} username]]
           [:div {:style {:display "flex" :marginTop "1rem"}}
            [:div {:style {:flex "0 0 12rem"}} "Link Expiration:"]
            [:div {:style {:flex "0 0 auto"}}
             (if expired?
               [:span {:style {:color "red"}} "Expired"]
               [:span {:style {:color (when expiring-soon? "red")}} (common/format-date expire-time)])
             [:div {}
              (links/create-external {:href (get-nih-link-href)} "Log-In to NIH to re-link your account")]]]
           (map
            (fn [whitelist]
              [:div {:style {:display "flex" :marginTop "1rem"}}
               [:div {:style {:flex "0 0 12rem"}} (str (:name whitelist) " Authorization:")]
               [:div {:style {:flex "0 0 auto"}}
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
            datasets)])]))
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


(react/defc- Form
  {:get-field-keys
   (fn []
     (list :firstName :lastName :title :contactEmail :institute :institutionalProgram :programLocationCity
           :programLocationState :programLocationCountry :pi))
   :get-values
   (fn [{:keys [state]}]
     (reduce-kv (fn [r k v] (assoc r k (string/trim v))) {} (merge @user-info/saved-user-profile (:values @state))))
   :validation-errors
   (fn [{:keys [refs this]}]
     (apply input/validate refs (map name (this :get-field-keys))))
   :render
   (fn [{:keys [this props state]}]
     (cond (:error-message @state) (style/create-server-error-message (:error-message @state))
           @user-info/saved-user-profile
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
                      (this :render-field :programLocationCountry "Country"))
            (common/clear-both)
            (when-not (:new-registration? props)
              [NihLink (select-keys props [:nih-token])])]
           :else (spinner "Loading User Profile...")))
   :render-radio-field
   (fn [{:keys [state]} key value]
     [:label {:style {:margin "0 1em 0.5em 0" :padding "0.5em 0"}}
      [:input {:type "radio" :value value :name key
               :checked (= value (get-in @state [:values key] (@user-info/saved-user-profile key)))
               :onChange #(swap! state assoc-in [:values key] value)}]
      value])
   :render-field
   (fn [{:keys [state]} key label & flags]
     (let [flag-set (set flags)
           required? (not (flag-set :optional))
           email? (flag-set :email)]
       [:div {:style {:margin "0.5em 1em 0.5em 0"}}
        (style/create-form-label label)
        [input/TextField {:style {:width 200}
                          :data-test-id key
                          :defaultValue (@user-info/saved-user-profile key)
                          :ref (name key)
                          :placeholder (when email? (utils/get-user-email))
                          :predicates [(when required? (input/nonempty label))
                                       (when email? (input/valid-email-or-empty label))]
                          :onChange #(swap! state assoc-in [:values key] (-> % .-target .-value))}]]))
   :component-will-mount
   (fn [{:keys [state]}]
     (when-not @user-info/saved-user-profile
       (user-info/reload-user-profile
        (fn [{:keys [success? status-text]}]
          (if success?
            (swap! state assoc :loaded-profile? true)
            (swap! state assoc :error-message status-text)))))
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/proxy-group (-> @utils/auth2-atom
                                            (.-currentUser) (.get) (.getBasicProfile) (.getEmail)))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (swap! state assoc :userProxyGroupEmail (get-parsed-response))))}))})


(react/defc- Page
  {:render
   (fn [{:keys [this props state]}]
     (let [new? (:new-registration? props)
           update? (:update-registration? props)]
       [:div {:style style/thin-page-style}
        [:h2 {} (cond new? "New User Registration"
                      update? "Update Registration"
                      :else "Profile")]
        [Form (merge {:ref "form"}
                     (select-keys props [:new-registration? :nih-token]))]
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
                                 (user-info/reload-user-profile)
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
    :make-path (fn [] "profile")}))
