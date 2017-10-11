(ns broadfcui.common.method.sync
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.links :as links]
   [broadfcui.common.method.messages :as messages]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(defn get-method-display [{:keys [namespace name snapshotId]}]
  (str namespace "/" name " snapshot " snapshotId))


(react/defc SynchronizeModal
  {:render
   (fn [{:keys [props state this]}]
     [modals/OKCancelForm
      {:header "Synchronize Access to Method"
       :dismiss (:dismiss props)
       :content
       (react/create-element
        [:div {:style {:maxWidth 670}}
         (messages/get-methods-repo-group-alert)
         [:div {:style {:marginBottom "1.5rem"}}
          (str "In order to allow other users of this "
               (:flavor props)
               " to run this method, you will need to grant them access to ")
          [:b {} (get-method-display (:method props))]
          "."]
         (if (:customize? @state)
           (this :-render-permission-detail)
           (links/create-internal {:onClick #(this :-setup-permission-detail)} "Customize Permissions"))
         [comps/ErrorViewer {:error (:grant-error @state)}]])
       :ok-button [buttons/Button {:text (if (:customize? @state)
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


(defn alert-modal [{:keys [method dismiss flavor]}]
  [modals/OKCancelForm
   {:header "Unable to Grant Method Access"
    :dismiss dismiss
    :show-cancel? false
    :ok-button "OK"
    :data-test-id "push-message"
    :content
    (let [owners (:managers method)
          method-display (get-method-display method)]
      [:div {:style {:maxWidth 670}}
       [:p {}
        (str "Users of this "
             flavor
             " may not have access to this method and be unable to run it.")]
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
