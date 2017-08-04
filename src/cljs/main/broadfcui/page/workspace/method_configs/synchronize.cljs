(ns broadfcui.page.workspace.method-configs.synchronize
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(defonce ^:private sync-flag (atom false))

(defn flag-synchronization []
  (reset! sync-flag true))

(defn- check-synchronization []
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
       :dismiss (:dismiss props)
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
           (links/create-internal {:onClick #(this :-setup-permission-detail)} "Customize Permissions"))
         [comps/ErrorViewer {:error (:grant-error @state)}]])
       :ok-button [comps/Button {:text (if (:customize? @state)
                                         "Grant Permission"
                                         "Grant Read Permission")
                                 :onClick #(this :-grant-permission)}]}])
   :-render-permission-detail
   (fn [{:keys [props locals]}]
     [:div {}
      [:p {} "Users currently without access:"]
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
                   ["Reader" "Owner" "No access"])]]])
             (:users props))]]])
   :-setup-permission-detail
   (fn [{:keys [props state locals]}]
     (reset! locals (->> (:users props)
                         (map (fn [user] {user "Reader"}))
                         (into {})))
     (swap! state assoc :customize? true))
   :-grant-permission
   (fn [{:keys [props state locals]}]
     (swap! state dissoc :grant-error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/persist-agora-entity-acl false (:method props))
       :payload (if (:customize? @state)
                  (->> @locals
                       (remove (fn [[_ role]] (= role "No access")))
                       (mapv (fn [[user role]] {:user user :role (string/upper-case role)})))
                  (mapv (fn [user] {:user user :role "READER"}) (:users props)))
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    ((:dismiss props))
                    (swap! state assoc :grant-error (get-parsed-response false))))}))})


(defn- alert-modal [{:keys [method dismiss]}]
  [modals/OKCancelForm
   {:header "Unable to Grant Method Access"
    :dismiss dismiss
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
         [:div {}
          [:p {}
           "Users will need to contact any owner of Method "
           [:b {} method-display]
           " and request access. Method owners:"]
          [:ul {}
           (map (fn [owner] [:li {} owner]) owners)]])])}])


(react/defc SyncContainer
  {:render
   (fn [{:keys [state]}]
     [:div {}
      (when-let [banner (:banner @state)]
        [comps/Blocker {:banner banner}])
      (when (:show-sync-modal? @state)
        [SynchronizeModal (merge (select-keys @state [:method :users])
                                 {:dismiss #(swap! state dissoc :show-sync-modal?)})])
      (when (:show-alert-modal? @state)
        (alert-modal (merge (select-keys @state [:method])
                            {:dismiss #(swap! state dissoc :show-alert-modal?)})))])
   :component-did-mount
   (fn [{:keys [props state this]}]
     (when (check-synchronization)
       (swap! state assoc :banner "Checking permissions...")
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-permission-report (:workspace-id props))
         :payload {:configs [(:config-id props)]}
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                    (swap! state dissoc :banner)
                    (if success?
                      (this :-perform-sync-logic (get-parsed-response))
                      (comps/push-error status-text)))})))
   :-perform-sync-logic
   (fn [{:keys [state]} parsed-perms-report]
     (let [method-report (first (:referencedMethods parsed-perms-report))]
       (when-not (get-in method-report [:method :public])
         (let [workspace-users (->> parsed-perms-report :workspaceACL keys (map name) set)
               me (utils/get-user-email)
               method-owner? (-> (get-in method-report [:method :managers]) set (contains? me))
               can-share? (get-in parsed-perms-report [:workspaceACL (keyword me) :canShare])
               method-users (when method-owner? (->> method-report :acls (map :user) set))
               unauthed-users (set/difference workspace-users method-users)]
           (cond (and method-owner? can-share? (seq unauthed-users))
                 (swap! state assoc
                        :show-sync-modal? true
                        :method (:method method-report)
                        :users unauthed-users)

                 (not (and method-owner? can-share?))
                 (swap! state assoc
                        :show-alert-modal? true
                        :method (:method method-report)))))))})
