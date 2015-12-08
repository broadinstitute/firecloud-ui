(ns org.broadinstitute.firecloud-ui.page.profile
  (:require
   clojure.string
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as components]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(react/defc Form
  {:get-values
   (fn [{:keys [state]}]
     (reduce-kv (fn [r k v] (assoc r k (clojure.string/trim v))) {} (:values @state)))
   :render
   (fn [{:keys [state this]}]
     (cond (:error-message @state) (style/create-server-error-message (:error-message @state))
           (:values @state) [:div {}
                             (react/call :render-field this :name "Full Name:")
                             (react/call :render-field this :email "Contact Email:")
                             (react/call :render-field this :institution "Institutional Affiliation:")
                             (react/call :render-field this :pi "Principal Investigator:")]
           :else [components/Spinner {:text "Loading User Profile..."}]))
   :render-field
   (fn [{:keys [state]} key label]
     [:div {}
      [:label {}
       (style/create-form-label label)
       (style/create-text-field
         {:style {:marginTop "0.167em" :width "30ex"}
          :value (get-in @state [:values key])
          :onChange #(swap! state assoc-in [:values key] (-> % .-target .-value))})]])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/profile-get
       (fn [{:keys [success? status-text get-parsed-response]}]
         (if success?
           (swap! state assoc :values (common/parse-profile (get-parsed-response)))
           (swap! state assoc :error-message status-text)))))})


(react/defc Page
  {:render
   (fn [{:keys [this props state]}]
     (let [new? (:new-registration? props)]
       [:div {:style {:marginTop "2em"}}
        [:h2 {} (if new? "New User Registration" "Profile")]
        [:div {:style {:marginBottom "1em"}}
         [Form {:ref "form"}]]
        (when-not (or (:in-progress? @state) (:done? @state))
          [components/Button {:text (if new? "Register" "Save") 
                              :onClick #(react/call :save this)}])
        (when (:done? @state)
          [:div {:style {:color (:success-green style/colors)}} "Profile saved!"])
        (when (:in-progress? @state)
          [components/Spinner {:text "Saving..."}])
        (when (:error-message @state)
          [:div {}
            [:div {:style {:color (:exception-red style/colors) :margin "10px 0px"}} (:error-message @state)]
            [components/ErrorViewer {:error (:server-error @state)}]])]))
   :save
   (fn [{:keys [this props state refs]}]
     (swap! state (fn [s] (assoc (dissoc s :error-message) :in-progress? true)))
     (let [values (react/call :get-values (@refs "form"))]
       (endpoints/profile-set values
         (fn [{:keys [success? get-parsed-response]}]
           (let [new-state (dissoc @state :in-progress?)]
             (if-not success?
               (reset! state (assoc new-state :error-message "Profile failed to save." :server-error (get-parsed-response)))
               (do (reset! state (assoc new-state :done? true))
                 (js/setTimeout (:on-done props) 2000))))))))})

(defn render [props]
  (react/create-element Page props))
