(ns org.broadinstitute.firecloud-ui.page.profile
  (:require
   clojure.string
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common.components :as components]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(react/defc Form
  {:get-values
   (fn [{:keys [state]}]
     (reduce-kv (fn [r k v] (assoc r k (clojure.string/trim v))) {} (:values @state)))
   :get-initial-state
   (fn [{:keys [props]}]
     {:values (:initial-values props)})
   :render
   (fn [{:keys [this]}]
     [:div {}
      (react/call :render-field this :name "Full Name:")
      (react/call :render-field this :email "Contact Email:")
      (react/call :render-field this :institution "Institutional Affiliation:")
      (react/call :render-field this :pi "Principal Investigator:")])
   :render-field
   (fn [{:keys [state]} key label]
     [:div {}
      [:label {}
       [:span {:style {:fontSize "88%"}} label] [:br]
       (style/create-text-field
        {:style {:marginTop "0.167em" :width "30ex"}
         :value (get-in @state [:values key])
         :onChange #(swap! state assoc-in [:values key] (-> % .-target .-value))})]])})


(react/defc Page
  {:render
   (fn [{:keys [this props state]}]
     (let [new? (:new-registration? props)]
       [:div {:style {:marginTop "2em"}}
        [:h2 {} (if new? "New User Registration" "Profile")]
        [:div {:style {:marginBottom "1em"}}
         [Form {:ref "form" :initial-values (:initial-values @state)}]]
        (when-not (or (:in-progress? @state) (:done? @state))
          [components/Button {:text (if new? "Register" "Save") 
                              :onClick #(react/call :save this)}])
        (when (:done? @state)
          [:div {:style {:color (:success-green style/colors)}} "Profile saved!"])
        (when (:in-progress? @state)
          [components/Spinner {:text "Saving..."}])
        (when (:error-message @state)
          [:div {:style {:color (:exception-red style/colors)}} (:error-message @state)])]))
   :save
   (fn [{:keys [this props state refs]}]
     (swap! state (fn [s] (assoc (dissoc s :error-message) :in-progress? true)))
     (let [values (react/call :get-values (@refs "form"))
           values (assoc values :isRegistrationComplete "true")
           errors (atom {})
           saved-count (atom 0)
           save-value (fn [k v]
                        (endpoints/profile-set
                         k v
                         (fn [{:keys [success?]}]
                           (swap! saved-count inc)
                           (when-not success?
                             (swap! errors assoc k "Save failed."))
                           (when (= @saved-count (count values))
                             (react/call :save-did-complete this @errors)))))]
       (doall (map #(apply save-value %) values))))
   :save-did-complete
   (fn [{:keys [props state]} errors]
     (let [new-state (dissoc @state :in-progress?)]
       (if (pos? (count errors))
         (reset! state (assoc new-state :error-message "Some fields failed to save."))
         (do (reset! state (assoc new-state :done? true))
             (js/setTimeout (:on-done props) 2000)))))})


(defn render [props]
  (react/create-element Page props))
