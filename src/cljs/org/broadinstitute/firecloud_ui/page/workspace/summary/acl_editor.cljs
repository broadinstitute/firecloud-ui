(ns org.broadinstitute.firecloud-ui.page.workspace.summary.acl-editor
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(def ^:private access-levels ["OWNER" "WRITER" "READER" "NO ACCESS"])


(defn- render-acl-content [props state this]
  [modal/OKCancelForm
   {:header
    (let [workspace-id (:workspace-id props)]
      (str "Permissions for " (:namespace workspace-id) "/" (:name workspace-id)))
    :content
    [:div {}
     (when (:saving? @state)
       [comps/Blocker {:banner "Updating..."}])
     [:span {:style {:fontSize "90%"}} "Project Owner(s)"]
     (map-indexed
      (fn [i acl-entry]
        [:div {:style {:padding "0.5em"}}
         (:email acl-entry)])
      (filter #(= (:accessLevel %) "PROJECT_OWNER") (:acl-vec @state)))
     [:div {:style {:padding "0.5em 0" :fontSize "90%" :marginTop "0.5em"}}
      [:div {:style {:float "left" :width 400}} "User ID"]
      [:div {:style {:float "right" :width 200 :marginLeft "1em"}} "Access Level"]
      (common/clear-both)]
     (map-indexed
      (fn [i acl-entry]
        [:div {}
         [input/TextField
          {:ref (str "acl-key" i)
           :predicates [(input/valid-email-or-empty "User ID")]
           :style {:float "left" :width 400 :color "black"
                   :backgroundColor (when (:read-only? acl-entry)
                                      (:background-light style/colors))}
           :disabled (:read-only? acl-entry)
           :spellCheck false
           :value (:email acl-entry)
           :onChange #(swap! state assoc-in [:acl-vec i :email] (.. % -target -value))}]
         (style/create-identity-select
          {:ref (str "acl-value" i)
           :style {:float "right" :width 200 :height 33 :marginLeft "1em"}
           :value (:accessLevel acl-entry)
           :onChange #(swap! state assoc-in [:acl-vec i :accessLevel]
                             (.. % -target -value))}
          access-levels)
         (common/clear-both)])
      (filter #(not= (:accessLevel %) "PROJECT_OWNER") (:acl-vec @state)))
     [:div {:style {:marginBottom "0.5em"}}
      [comps/Button {:text "Add new" :icon :add
                     :onClick #(swap! state update-in [:acl-vec]
                                      conj {:email "" :accessLevel "READER"})}]]
     (style/create-validation-error-message (:validation-error @state))
     [comps/ErrorViewer {:error (:save-error @state)}]]
    :ok-button {:text "Save" :onClick #(react/call :persist-acl this)}}])

(react/defc AclEditor
  {:render
   (fn [{:keys [props state this]}]
     (if (:acl-vec @state)
       (render-acl-content props state this)
       [:div {:style {:padding "2em"}}
        (if (:load-error @state)
          (style/create-server-error-message (:load-error @state))
          [comps/Spinner {:text "Loading Permissions..."}])]))
   :persist-acl
   (fn [{:keys [props state refs]}]
     (swap! state dissoc :save-error :validation-error)
     (let [filtered-acl (->> (:acl-vec @state)
                             (map #(dissoc % :read-only?))
                             (map #(update-in % [:email] clojure.string/trim))
                             (filter #(not (empty? (:email %)))))
           fails (apply input/validate refs (map #(str "acl-key" %) (range (count (:acl-vec @state)))))]
       (if fails
         (swap! state assoc :validation-error fails)
         (do
           (swap! state assoc :saving? true)
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/update-workspace-acl (:workspace-id props))
              :headers utils/content-type=json
              :payload filtered-acl
              :on-done (fn [{:keys [success? get-parsed-response]}]
                         (swap! state dissoc :saving?)
                         (if success?
                           (do
                             ((:request-refresh props))
                             (modal/pop-modal))
                           (swap! state assoc :save-error (get-parsed-response))))})))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace-acl (:workspace-id props))
        :on-done
        (fn [{:keys [success? get-parsed-response]}]
          (if success?
            (swap! state assoc :acl-vec
                   (mapv (fn [[k v]]
                           {:email k :accessLevel v :read-only? true})
                         (get-parsed-response)))
            (swap! state assoc :load-error (get-parsed-response))))}))})
