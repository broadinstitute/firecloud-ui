(ns broadfcui.common.management-utils
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.input :as input]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table :as table]
    [broadfcui.common.table-utils :refer [add-right]]
    [broadfcui.common.table-style :as table-style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.utils :as utils]
    ))

(react/defc AddUserDialog
  {:render
   (fn [{:keys [props state this refs]}]
     [comps/OKCancelForm
      {:header (str "Add user to " (:group-name props))
       :ok-button #(this :add-user)
       :get-first-element-dom-node #(react/find-dom-node (@refs "email"))
       :content
       (react/create-element
        [:div {:style {:width 420}}
         (when (:adding? @state)
           [comps/Blocker {:banner "Adding user..."}])
         [:div {:style {:display "flex"}}
          [:div {:style {:flex "1 1 auto"}}
           (style/create-form-label "User email")
           [input/TextField {:ref "email" :style {:width "100%"}
                             :predicates [(input/valid-email "Email")]
                             :onKeyDown (common/create-key-handler [:enter] #(this :add-user))}]]
          [:div {:style {:flex "0 0 10px"}}]
          [:div {:style {:flex "0 0 100px"}}
           (style/create-form-label "Role")
           (style/create-identity-select {:ref "role"} ["User" "Owner"])]]
         (:footer props)
         (style/create-validation-error-message (:fails @state))
         [comps/ErrorViewer {:error (:server-error @state)
                             :expect {404 "This is not a registered user"}}]])}])
   :add-user
   (fn [{:keys [props state refs]}]
     (let [[email & fails] (input/get-and-validate refs "email")]
       (swap! state assoc :fails fails :server-error nil)
       (when-not fails
         (let [role (common/get-text refs "role")
               {:keys [endpoint on-add]} props]
           (swap! state assoc :adding? true)
           (endpoints/call-ajax-orch
            {:endpoint (endpoint (:group-name props) role email)
             :on-done (fn [{:keys [success? get-parsed-response]}]
                        (swap! state dissoc :adding?)
                        (if success?
                          (do (modal/pop-modal)
                              (on-add))
                          (swap! state assoc :server-error (get-parsed-response false))))})))))})


(defn- remove-user [endpoint state this]
  (swap! state assoc :removing? true)
  (endpoints/call-ajax-orch
   {:endpoint endpoint
    :on-done (fn [{:keys [success? get-parsed-response]}]
               (swap! state dissoc :removing?)
               (if success?
                 (this :load)
                 (swap! state assoc :remove-error (get-parsed-response false))))}))


(react/defc MembershipManagementPage
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [load-error data]} @state
           {:keys [header delete-endpoint table-data]} props]
       (cond load-error [comps/ErrorViewer {:error load-error}]
             (not data) [comps/Spinner {:text "Loading..."}]
             :else
             [:div {:style {:position "relative"}}
              (when header
                (header (:data @state)))
              (when (:removing? @state)
                [comps/Blocker {:banner "Removing user..."}])
              [table/Table
               {:header-row-style table-style/header-row-style-light
                :header-style {:padding "0.5em 0 0.5em 14px"}
                :cell-content-style {:padding 0 :paddingRight 20 :marginRight -20}
                :row-style {:backgroundColor "white"}
                :reorderable-columns? false
                :resize-tab-color (:line-default style/colors)
                :toolbar (add-right
                          [comps/Button {:text "Add User..." :icon :add-new
                                         :onClick (fn [_]
                                                    (modal/push-modal
                                                     [AddUserDialog {:endpoint (:add-endpoint props)
                                                                     :group-name (:group-name props)
                                                                     :on-add #(this :load)
                                                                     :footer (:add-member-footer props)}]))}])
                :columns [{:header "Email" :starting-width 500
                           :content-renderer
                           (fn [email]
                             [:div {:style table-style/table-cell-plank-left}
                              email])}
                          {:header "Role" :starting-width 100 :resizable? false :sort-initial :asc
                           :content-renderer
                           (fn [role]
                             [:div {:style table-style/table-cell-plank-right}
                              role])}
                          {:starting-width :remaining
                           :filter-by :none :sort-by :none :resizable? false
                           :as-text
                           (fn [{:keys [email role]}]
                             (str "Remove " (clojure.string/lower-case role) " " email))
                           :content-renderer
                           (fn [{:keys [email role]}]
                             (style/create-link {:text "Remove"
                                                 :onClick #(remove-user
                                                            (delete-endpoint (:group-name props) role email)
                                                            state
                                                            this)}))}]
                :data (table-data data)
                :->row (fn [{:keys [email role] :as row}]
                         [email
                          role
                          row])}]
              [comps/ErrorViewer {:error (:remove-error @state)}]])))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :load))
   :load
   (fn [{:keys [props state]}]
     (let [endpoint (:list-endpoint props)]
       (swap! state dissoc :data :load-error)
       (endpoints/call-ajax-orch
        {:endpoint (endpoint (:group-name props))
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state assoc (if success? :data :load-error) (get-parsed-response)))})))})
