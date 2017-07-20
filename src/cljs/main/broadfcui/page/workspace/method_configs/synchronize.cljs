(ns broadfcui.page.workspace.method-configs.synchronize
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(defonce ^:private sync-flag (atom false))

(defn flag-synchronization []
  (reset! sync-flag true))

(defn check-synchronization []
  (let [val @sync-flag]
    (reset! sync-flag false)
    val))


(defn- get-method-display [{:keys [namespace name snapshotId]}]
  (str namespace "/" name " snapshot " snapshotId))

(react/defc- SynchronizeModal
  {:render
   (fn [{:keys [props state this]}]
     [modals/OKCancelForm
      {:header "Synchronize Access to Method"
       :dismiss modal/pop-modal
       :content
       (react/create-element
        [:div {:style {:maxWidth 670}}
         [:div {:style {:marginBottom "1.5rem"}}
          "In order to allow other users of this workspace to run this method, you will need
           to grant them access to "
          [:b {} (get-method-display (:method props))]
          "."]
         (if (:customize? @state)
           (this :-render-permission-detail)
           (style/create-link {:text "Customize Permissions"
                               :onClick #(swap! state assoc :customize? true)}))
         [comps/ErrorViewer {:error (:grant-error @state)}]])
       :ok-button [comps/Button {:text (if (:customize? @state)
                                         "Grant Permission"
                                         "Grant Read Permission")
                                 :onClick #(this :-grant-permission)}]}])
   :-render-permission-detail
   (fn [{:keys [props locals]}]
     [:table {}
      [:tbody {}
       (map (fn [user]
              [:tr {}
               [:td {:style {:verticalAlign "baseline"}} user]
               [:td {:style {:verticalAlign "baseline"}}
                [:div {:style {:marginLeft "1rem"}}
                 (style/create-identity-select
                  {:style {:width 120}
                   :onChange #(swap! locals assoc user (.. % -target -value))}
                  ["No access" "Reader" "Owner"])]]])
            (:users props))]])
   :-grant-permission
   (fn [{:keys [props state locals]}]
     (swap! state dissoc :grant-error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/persist-agora-entity-acl false (:method props))
       :payload (if (:customize? @state)
                  (mapv (fn [[user level]] {:user user :role (string/upper-case level)}) @locals)
                  (mapv (fn [user] {:user user :role "READER"}) (:users props)))
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (modal/pop-modal)
                    (swap! state assoc :grant-error (get-parsed-response false))))}))})


(defn- alert-modal [method]
  [modals/OKCancelForm
   {:header "Unable to Grant Method Access"
    :dismiss modal/pop-modal
    :show-cancel? false
    :ok-button "OK"
    :content
    (let [owners (:managers method)
          method-display (get-method-display method)]
      [:div {:style {:maxWidth 670}}
       [:p {} "Users of this configuration may not have access to this method and be unable to run it."]
       (if (= 1 (count owners))
         [:p {}
          "Users will need to contact the Method owner "
          [:b {} (first owners)]
          " and request access to method "
          [:b {} method-display]
          "."]
         [:p {}
          "Users will need to contact an owner of Method "
          [:b {} method-display]
          " and request access. Method owners:"
          [:ul {}
           (map (fn [owner] [:li {} owner]) owners)]])])}])


(defn handle-sync [parsed-perms-report]
  (let [workspace-users (->> parsed-perms-report :workspaceACL keys (map name) set)
        method-report (first (:referencedMethods parsed-perms-report)) ;; THERE CAN BE ONLY ONE
        me (utils/get-user-email)
        method-owner? (-> (get-in method-report [:method :managers]) set (contains? me))
        can-share? (get-in parsed-perms-report [:workspaceACL (keyword me) :canShare])
        method-users (when method-owner? (->> method-report :acls (map :user) set))
        unauthed-users (set/difference workspace-users method-users)]
    (cond (and method-owner? can-share? (seq unauthed-users))
          (modal/push-modal [SynchronizeModal {:method (:method method-report)
                                               :users unauthed-users}])
          (or (not method-owner?) (not can-share?))
          (modal/push-modal (alert-modal (:method method-report))))))
