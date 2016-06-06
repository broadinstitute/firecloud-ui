(ns org.broadinstitute.firecloud-ui.page.profile
  (:require
   cljsjs.moment
   clojure.string
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as components]
   [org.broadinstitute.firecloud-ui.common.icons :as icons]
   [org.broadinstitute.firecloud-ui.common.input :as input]
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


(react/defc NihLink
  {:render
   (fn [{:keys [state]}]
     (let [status (:nih-status @state)
           username (get status "linkedNihUsername")
           expire-time (-> (get status "linkExpireTime") (* 1000) (js/moment.))
           expired? (.isBefore expire-time (js/moment.))
           _24-hours-from-now (.add (js/moment.) 24 "hours")
           expiring-soon? (.isBefore expire-time _24-hours-from-now)
           authorized? (get status "isDbgapAuthorized")]
       [:div {}
        [:h3 {} "Linked NIH Account"]
        (cond
          (:error-message @state) (style/create-server-error-message (:error-message @state))
          (:pending-nih-username-token @state)
          [components/Spinner {:ref "pending-spinner" :text "Linking NIH account..."}]
          (nil? username)
          [:a {:href (get-nih-link-href)}
           "Log-In to NIH to link your account"]
          :else
          [:div {}
           [:div {:style {:display "flex"}}
            [:div {:style {:flex "0 0 20ex"}} "eRA Commons / NIH Username:"]
            [:div {:style {:flex "0 0 auto"}} username]]
           [:div {:style {:display "flex" :marginTop "1em"}}
            [:div {:style {:flex "0 0 20ex"}} "Link Expiration:"]
            [:div {:style {:flex "0 0 auto"}}
             (if expired?
               [:span {:style {:color "red"}} "Expired"]
               [:span {:style {:color (when expiring-soon? "red")}} (.format expire-time "LLL")])
             [:br]
             [:a {:href (get-nih-link-href)}
              "Log-In to NIH to re-link your account"]]]
           [:div {:style {:display "flex" :marginTop "1em"}}
            [:div {:style {:flex "0 0 20ex"}} "dbGaP Authorization:"]
            [:div {:style {:flex "0 0 auto"}}
             (if authorized?
               [:span {:style {:color (:success-green style/colors)}} "Authorized"]
               [:span {:style {:color (:text-gray style/colors)}} "Not Authorized"])]]])]))
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
         (react/call :load-nih-status this))))
   :component-did-update
   (fn [{:keys [prev-state state refs]}]
     (when (@refs "pending-spinner")
       (common/scroll-to-center (-> (@refs "pending-spinner") react/find-dom-node))))
   :load-nih-status
   (fn [{:keys [props state]}]
     (endpoints/profile-get-nih-status
      (fn [{:keys [success? status-code status-text get-parsed-response]}]
        (cond
          success? (swap! state assoc :nih-status (get-parsed-response))
          (= status-code 404) (swap! state assoc :nih-status :none)
          :else
          (swap! state assoc :error-message status-text)))))
   :link-nih-account
   (fn [{:keys [this state]} token]
     (endpoints/profile-link-nih-account
      token
      (fn [{:keys [success?]}]
        (if success?
          (do (swap! state dissoc :pending-nih-username-token :nih-status)
              (react/call :load-nih-status this))
          (swap! state assoc :error-message "Failed to link NIH account")))))})


(react/defc Form
  {:get-field-keys
   (fn []
     (list :firstName :lastName :title :contactEmail :institute :institutionalProgram :programLocationCity
           :programLocationState :programLocationCountry :pi))
   :get-values
   (fn [{:keys [state refs this]}]
     (reduce-kv (fn [r k v] (assoc r k (clojure.string/trim v))) {} (:values @state)))
   :validation-errors
   (fn [{:keys [refs this]}]
     (apply input/validate refs (map name (react/call :get-field-keys this))))
   :render
   (fn [{:keys [this props state]}]
     (cond (:error-message @state) (style/create-server-error-message (:error-message @state))
           (:values @state)
           [:div {}
            [:div {:style {:fontWeight "bold" :margin "1em 0 1em 0"}} "* - required fields"]
            (react/call :render-field this :firstName "First Name" true)
            (react/call :render-field this :lastName "Last Name" true)
            (react/call :render-field this :title "Title" true)
            (react/call :render-field this :contactEmail "Contact Email (to receive FireCloud notifications)" false true)
            (react/call :render-field this :institute "Institute" true)
            (react/call :render-field this :institutionalProgram "Institutional Program" true)
            [:div {}
              [:span {:style {:fontSize "88%"}} "Program Location:"]
              [:div {}
                (react/call :render-nested-field this :programLocationCity "City" true)
                (react/call :render-nested-field this :programLocationState "State/Province" true)
                (react/call :render-nested-field this :programLocationCountry "Country" true)]]
                (react/call :render-field this :pi "Principal Investigator/Program Lead" true)
            [:div {:style {:marginBottom "1em" :fontSize "88%"}} "*NonProfit Status"
              (react/call :render-radio-field this :nonProfitStatus "Profit")
              (react/call :render-radio-field this :nonProfitStatus "Non-Profit")]
            (when-not (:new-registration? props)
              [:div {} [NihLink {:parent-nav-context (:parent-nav-context props)}]])]
           :else [components/Spinner {:text "Loading User Profile..."}]))
   :render-radio-field
   (fn [{:keys [state]} key value]
       [:div {:style {:clear "both" :marginTop "0.167em" :width "30ex"}}
        [:label {}
         [:input {:type "radio" :value value :name key
                  :checked (= (get-in @state [:values key]) value)
                  :onChange #(swap! state assoc-in [:values key] value)}]
         value]])
   :render-nested-field
   (fn [{:keys [state]} key label required]
       [:div {:style {:float "left"}}
        [:label {}
         [:div {:style {:marginBottom "0.16667em" :fontSize "88%"}} (str (when required "*") label ":")]]
        [input/TextField {:style {:marginRight "1em"}
                          :defaultValue (get-in @state [:values key])
                          :ref (name key) :placeholder (get-in @state [:values key])
                          :predicates [(when required (input/nonempty label))]
                          :onChange #(swap! state assoc-in [:values key] (-> % .-target .-value))}]])
   :render-field
   (fn [{:keys [state]} key label required valid-email-or-empty]
     [:div {:style {:clear "both"}}
      [:label {}
       (style/create-form-label (str (when required "*") label ":"))
       [input/TextField {:defaultValue (get-in @state [:values key])
                         :ref (name key) :placeholder (get-in @state [:values key])
                         :predicates [(when required (input/nonempty label))
                                      (when valid-email-or-empty (input/valid-email-or-empty label))]
                         :onChange #(swap! state assoc-in [:values key] (-> % .-target .-value))}]]])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/profile-get
      (fn [{:keys [success? status-text get-parsed-response]}]
        (if success?
          (let [parsed (get-parsed-response)]
            (swap! state assoc :values (common/parse-profile parsed)))
          (swap! state assoc :error-message status-text)))))})


(react/defc Page
  {:render
   (fn [{:keys [this props state]}]
     (let [new? (:new-registration? props)]
       [:div {:style {:marginTop "2em"}}
        [:h2 {} (if new? "New User Registration" "Profile")]
        [:div {}
         [Form {:ref "form" :parent-nav-context (:nav-context props)
                :new-registration? (:new-registration? props)}]]
        [:div {:style {:marginTop "2em"}}
         (when (:server-error @state)
           [:div {:style {:marginBottom "1em"}}
            [components/ErrorViewer {:error (:server-error @state)}]])
         (when (:validation-errors @state)
           [:div {:style {:marginBottom "1em"}}
            [:span {:style {:paddingRight "1ex"}}
             (icons/font-icon {:style {:color (:exception-red style/colors)}}
               :status-warning-triangle)] "Validation Errors: "
            [:ul {}
             (map (fn [e] [:li {} e]) (:validation-errors @state))]])
         (cond
           (:done? @state)
           [:div {:style {:color (:success-green style/colors)}} "Profile saved!"]
           (:in-progress? @state)
           [components/Spinner {:text "Saving..."}]
           :else
           [components/Button {:text (if new? "Register" "Save Profile")
                               :onClick #(react/call :save this)}])]]))
   :save
   (fn [{:keys [this props state refs]}]
     (swap! state (fn [s] (assoc (dissoc s :server-error :validation-errors) :in-progress? true)))
     (let [values (react/call :get-values (@refs "form"))
           validation-errors (react/call :validation-errors (@refs "form"))]
       (cond
         (nil? validation-errors)
         (endpoints/profile-set
           values
           (fn [{:keys [success? get-parsed-response]}]
             (swap! state (fn [s]
                            (let [new-state (dissoc s :in-progress? :validation-errors)]
                              (if-not success?
                                (assoc new-state :server-error (get-parsed-response))
                                (let [on-done (or (:on-done props) #(swap! state dissoc :done?))]
                                  (js/setTimeout on-done 2000)
                                  (assoc new-state :done? true))))))))
         :else
         (swap! state (fn [s]
                        (let [new-state (dissoc s :in-progress? :done?)]
                          (assoc new-state :validation-errors validation-errors)))))))})

(defn render [props]
  (react/create-element Page props))
