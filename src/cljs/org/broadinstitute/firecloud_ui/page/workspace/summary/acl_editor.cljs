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


(defn- render-acl-content [props state component]
  [modal/OKCancelForm
   {:header
    (let [workspace-id (:workspace-id props)]
      (str "Permissions for " (:namespace workspace-id) "/" (:name workspace-id)))
    :content
    (react/create-element
     [:div {}
      (when (:saving? @state)
        [comps/Blocker {:banner "Updating..."}])
      [:div {:style {:padding "0.5rem 0" :fontSize "90%"}} "Project Owner(s)"]
      (map-indexed
       (fn [i acl-entry]
         [:div {:style {:padding "0.5rem 0" :borderTop style/standard-line}}
          (:email acl-entry)])
       (:project-owner-acl-vec @state))
      [:div {:style {:padding "0.5rem 0" :fontSize "90%" :marginTop "0.5rem"}}
       [:div {:style {:display "inline-block" :width 400}} "User ID"]
       [:div {:style {:display "inline-block" :width 200 :marginLeft "1rem"}} "Access Level"]]
      (map-indexed
       (fn [i acl-entry]
         [:div {:style {:borderTop style/standard-line :padding "0.5rem 0"}}
          (if (:read-only? acl-entry)
            [:div {:style {:display "inline-block" :width 400 :fontSize "88%"}}
             (:email acl-entry)]
            [input/TextField
             {:ref (str "acl-key" i)
              :predicates [(input/valid-email-or-empty "User ID")]
              :style {:display "inline-block" :width 400 :marginBottom 0}
              :spellCheck false
              :value (:email acl-entry)
              :onChange #(swap! state assoc-in [:non-project-owner-acl-vec i :email] (.. % -target -value))}])
          (style/create-identity-select
           {:ref (str "acl-value" i)
            :style {:display "inline-block" :width 200 :height 33 :marginLeft "1rem" :marginBottom 0}
            :disabled (= (:email acl-entry) (:userEmail @utils/current-user-info))
            :value (:accessLevel acl-entry)
            :onChange #(swap! state assoc-in [:non-project-owner-acl-vec i :accessLevel]
                              (.. % -target -value))}
           access-levels)])
       (:non-project-owner-acl-vec @state))
      [:div {:style {:margin "0.5rem 0"}}
       [comps/Button {:text "Add new" :icon :add
                      :onClick #(swap! state update-in [:non-project-owner-acl-vec]
                                       conj {:email "" :accessLevel "READER"})}]]
      (style/create-validation-error-message (:validation-error @state))
      [comps/ErrorViewer {:error (:save-error @state)}]])
    :ok-button {:text "Save" :onClick #(react/call :persist-acl component)}}])

(react/defc AclEditor
  {:render
   (fn [{:keys [props state this]}]
     (if (or (:non-project-owner-acl-vec @state) (:project-owner-acl-vec @state))
       (render-acl-content props state this)
       [:div {:style {:padding "2em"}}
        (if (:load-error @state)
          (style/create-server-error-message (:load-error @state))
          [comps/Spinner {:text "Loading Permissions..."}])]))
   :persist-acl
   (fn [{:keys [props state refs]}]
     (swap! state dissoc :save-error :validation-error)
     (let [filtered-acl (->> (concat (:project-owner-acl-vec @state) (:non-project-owner-acl-vec @state))
                             (map #(dissoc % :read-only?))
                             (map #(update-in % [:email] clojure.string/trim))
                             (filter #(not (empty? (:email %)))))
           fails (apply input/validate refs (filter
                                             #(contains? @refs %)
                                             (map #(str "acl-key" %) (range (count (:non-project-owner-acl-vec @state))))))]
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
                          (swap! state assoc :save-error (get-parsed-response false))))})))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-workspace-acl (:workspace-id props))
       :on-done
       (fn [{:keys [success? get-parsed-response]}]
         (if success?
           (swap! state
                  #(reduce
                    (fn [state [k v]]
                      (update state
                              (if (= v "PROJECT_OWNER")
                                :project-owner-acl-vec
                                :non-project-owner-acl-vec)
                              conj {:email k :accessLevel v :read-only? true}))
                    (assoc % :project-owner-acl-vec []
                             :non-project-owner-acl-vec [])
                    (get-parsed-response false)))
           (swap! state assoc :load-error (get-parsed-response false))))}))})
