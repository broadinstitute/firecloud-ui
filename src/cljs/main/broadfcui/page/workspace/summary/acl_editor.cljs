(ns broadfcui.page.workspace.summary.acl-editor
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))

; The list of all assignable access levels in the system
; Note that if you add an access level, you will want to add it in common.cljs as well
(def ^:private access-levels ["OWNER" "WRITER" "READER" "NO ACCESS"])


(react/defc AclEditor
  {:render
   (fn [{:keys [props state this]}]
     (if (or (:non-project-owner-acl-vec @state) (:project-owner-acl-vec @state))
       (if (:offering-invites? @state)
         (this :-render-invite-offer)
         (this :-render-acl-content))
       [:div {:style {:padding "2em"}}
        (if (:load-error @state)
          (style/create-server-error-message (:load-error @state))
          [comps/Spinner {:text "Loading Permissions..."}])]))
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
                              (if (= (:accessLevel v) "PROJECT_OWNER")
                                :project-owner-acl-vec
                                :non-project-owner-acl-vec)
                              conj {:email (name k)
                                    :accessLevel (:accessLevel v)
                                    :pending? (:pending v)
                                    :canShare (:canShare v)
                                    :read-only? true}))
                    (assoc % :project-owner-acl-vec []
                             :non-project-owner-acl-vec [])
                    (:acl (get-parsed-response))))
           (swap! state assoc :load-error (get-parsed-response false))))})
     (endpoints/get-groups
      (fn [_ groups]
        (swap! state assoc :user-groups groups))))
   :-render-acl-content
   (fn [{:keys [props state this]}]
     (let [{:keys [workspace-id user-access-level ]} props]
  [comps/OKCancelForm
   {:header
    (str "Permissions for " (:namespace workspace-id) "/" (:name workspace-id))
    :content
    (react/create-element
     [:div {}
      (when (:saving? @state)
        [comps/Blocker {:banner "Updating..."}])
      [:div {:style {:padding "0.5rem 0" :fontSize "90%"}} "Billing Project Owner(s)"]
      (map
       (fn [acl-entry]
         [:div {:style {:padding "0.5rem 0" :fontSize "90%" :borderTop style/standard-line}}
          (:email acl-entry)])
       (:project-owner-acl-vec @state))
      [:div {:style {:padding "0.5rem 0" :fontSize "90%" :marginTop "0.5rem"}}
       [:div {:style {:display "inline-block" :width 400}} "User ID"]
       [:div {:style {:display "inline-block" :width 200 :marginLeft "1rem"}} "Access Level"]
       (when (common/access-greater-than-equal-to? user-access-level "OWNER")
         [:div {:style {:display "inline-block" :width 80 :marginLeft "1rem"}} "Can Share"])]
      [:datalist {:id "groups-datalist"}
       (when-let [groups (:user-groups @state)]
         (map (fn [group]
                [:option {:value (:groupEmail group)}])
              groups))]
      (map-indexed
       (fn [i acl-entry]
         [:div {:style {:borderTop style/standard-line :padding "0.5rem 0"}}
          (if (:read-only? acl-entry)
            [:div {:style {:display "inline-block" :width 400 :fontSize "88%"}}
             (:email acl-entry)]
            [input/TextField
             {:ref (str "acl-key" i) :autoFocus true
              :predicates [(input/valid-email-or-empty "User ID")]
              :style {:display "inline-block" :width 400 :marginBottom 0}
              :spellCheck false
              :value (:email acl-entry)
              :list "groups-datalist"
              :onChange #(swap! state assoc-in [:non-project-owner-acl-vec i :email] (.. % -target -value))}])
          (let [available-access-levels (filter #(common/access-greater-than-equal-to? user-access-level %) access-levels)
                disabled? (or (common/access-greater-than? (:accessLevel acl-entry) user-access-level)
                              (= (:email acl-entry) (utils/get-user-email)))]
            (style/create-identity-select
             {:ref (str "acl-value" i)
              :style {:display "inline-block" :width 200 :height 33 :marginLeft "1rem" :marginBottom 0}
              :disabled disabled?
              :value (:accessLevel acl-entry)
              :onChange #(swap! state assoc-in [:non-project-owner-acl-vec i :accessLevel] (.. % -target -value))}
             (if disabled? access-levels available-access-levels)))
          (if (common/access-greater-than-equal-to? user-access-level "OWNER")
            [:label {:style {:marginLeft "1rem" :cursor "pointer" :verticalAlign "middle" :display "inline-block" :width 80 :textAlign "center"}}
             [:input {:type "checkbox"
                      :style {:verticalAlign "middle" :float "none"}
                      :onChange #(swap! state assoc-in [:non-project-owner-acl-vec i :canShare] (.. % -target -checked))
                      :disabled (common/access-greater-than-equal-to? (:accessLevel acl-entry) "OWNER")
                      :checked (or (:canShare acl-entry) (common/access-equal-to? (:accessLevel acl-entry) "OWNER"))}]])
          (when (:pending? acl-entry)
            [:span {:style {:fontStyle "italic" :color (:text-light style/colors) :marginLeft "1rem"}}
             "Pending..."])])
       (:non-project-owner-acl-vec @state))
      [:div {:style {:margin "0.5rem 0"}}
       [comps/Button {:text "Add new" :icon :add-new
                      :data-test-id (config/when-debug "add-new-acl-button")
                      :onClick #(swap! state update :non-project-owner-acl-vec
                                       conj (let [permissions {:email "" :accessLevel "READER"}]
                                              ; Only owners can set new canShare permissions, so we only want to include
                                              ; those in the default settings when the user is at least an owner
                                              (if (common/access-greater-than-equal-to? user-access-level "OWNER")
                                                (assoc permissions :canShare false)
                                                permissions)))}]]
      (style/create-validation-error-message (:validation-error @state))
      [comps/ErrorViewer {:error (:save-error @state)}]])
    :ok-button {:text "Save" :onClick #(this :-persist-aclfalse)}}]))

  :-render-invite-offer(fn [{:keys [props state this]}]
     (let [{:keys [workspace-id ]} props]
  [comps/OKCancelForm
   {:header (str "Invite new users to " (:namespace workspace-id) "/" (:name workspace-id))
    :content (react/create-element
              [:div {}
               (when (:saving? @state)
                 [comps/Blocker {:banner "Updating..."}])
               [:div {:style {:padding "0.5rem 0" :fontSize "90%" :marginTop "0.5rem"}}
                [:div {:style {:display "inline-block" :width 400}} "User ID"]
                [:div {:style {:display "inline-block" :width 200 :marginLeft "1rem"}} "Access Level"]]
               (map
                (fn [ acl-entry]
                  [:div {:style {:borderTop style/standard-line :padding "0.5rem 0"}}
                   [:div {:style {:display "inline-block" :width 400 :fontSize "88%"}}
                    (:email acl-entry)]
                   [:span {:style {:display "inline-block" :lineHeight "33px"
                                   :width 200 :height 33 :verticalAlign "middle"
                                   :marginLeft "1rem" :marginBottom 0}}
                    (:accessLevel acl-entry)]])
                (:users-not-found @state))])
    :ok-button {:text "Invite" :onClick #(this :-persist-acl true)}}]))
   :-persist-acl
   (fn [{:keys [props state refs this]} invite-new?]
     (swap! state dissoc :save-error :validation-error)
     (let [filtered-acl (->> (concat (:project-owner-acl-vec @state) (:non-project-owner-acl-vec @state))
                             (map #(dissoc % :read-only?))
                             (map #(update % :email clojure.string/trim))
                             (remove (comp empty? :email)))
           grant-filtered-acl (if (common/access-greater-than-equal-to? (:user-access-level props) "OWNER")
                                filtered-acl
                                (map #(dissoc % :canShare) filtered-acl))
           fails (->> (:non-project-owner-acl-vec @state) count range
                      (map (partial str "acl-key"))
                      (filter @refs)
                      (apply input/validate refs))]
       (if fails
         (swap! state assoc :validation-error fails)
         (do
           (swap! state assoc :saving? true)
           (endpoints/call-ajax-orch
            {:endpoint (endpoints/update-workspace-acl (:workspace-id props) invite-new?)
             :headers utils/content-type=json
             :payload grant-filtered-acl
             :on-done (fn [{:keys [success? get-parsed-response]}]
                        (swap! state dissoc :saving?)
                        (if-let [users-not-found (seq (:usersNotFound (get-parsed-response)))]
                          (this :-offer-user-invites users-not-found)
                          (if success?
                            (do
                              ((:request-refresh props))
                              (modal/pop-modal))
                            (swap! state assoc :save-error (get-parsed-response false)))))})))))
   :-offer-user-invites
   (fn [{:keys [state]} users-not-found]
     (swap! state assoc :users-not-found users-not-found :offering-invites? true))})
