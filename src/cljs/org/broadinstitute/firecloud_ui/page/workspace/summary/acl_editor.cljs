(ns org.broadinstitute.firecloud-ui.page.workspace.summary.acl-editor
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(def ^:private access-levels ["OWNER" "WRITER" "READER" "NO ACCESS"])
(def ^:private column-width "calc(50% - 4px)")


(defn- render-acl-content [props state this]
  [dialog/OKCancelForm
   {:header
    (let [workspace-id (:workspace-id props)]
      (str "Permissions for " (:namespace workspace-id) "/" (:name workspace-id)))
    :content
    (react/create-element
      [:div {}
       (when (:saving? @state)
         [comps/Blocker {:banner "Updating..."}])
       [:div {:style {:padding "0.5em 0" :fontSize "90%"}}
        [:div {:style {:float "left" :width column-width}} "User or Group ID"]
        [:div {:style {:float "right" :width column-width}} "Access Level"]
        (common/clear-both)]
       (map-indexed
         (fn [i acl-entry]
           [:div {}
            [input/TextField
             {:ref (str "acl-key" i)
              :predicates [(input/valid-email-or-empty "User or Group ID")]
              :style {:float "left" :width column-width :color "black"
                      :backgroundColor (when (:read-only? acl-entry)
                                         (:background-gray style/colors))}
              :disabled (:read-only? acl-entry)
              :spellCheck false
              :value (:email acl-entry)
              :onChange #(swap! state assoc-in [:acl-vec i :email] %)}]
            (style/create-identity-select
              {:ref (str "acl-value" i)
               :style {:float "right" :width column-width :height 33}
               :value (:accessLevel acl-entry)
               :onChange #(swap! state assoc-in [:acl-vec i :accessLevel]
                                 (.. % -target -value))}
              access-levels)
            (common/clear-both)])
         (:acl-vec @state))
       [:div {:style {:marginBottom "0.5em"}}
        [comps/Button {:text "Add new" :style :add
                       :onClick #(swap! state update-in [:acl-vec]
                                        conj {:email "" :accessLevel "READER"})}]]
       (style/create-validation-error-message (:validation-error @state))
       [comps/ErrorViewer {:error (:save-error @state)}]])
    :dismiss-self (:dismiss-self props)
    :ok-button [comps/Button {:text "Save" :onClick #(react/call :persist-acl this)}]}])

(react/defc AclEditor
  {:render
   (fn [{:keys [props state this]}]
     [dialog/Dialog
      {:width "50%"
       :dismiss-self (:dismiss-self props)
       :content
       (react/create-element
         (if (:acl-vec @state)
           (render-acl-content props state this)
           [:div {:style {:padding "2em"}}
            (if (:load-error @state)
              (style/create-server-error-message (:load-error @state))
              [comps/Spinner {:text "Loading Permissions..."}])]))}])
   :persist-acl
   (fn [{:keys [props state refs this]}]
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
              :headers {"Content-Type" "application/json"}
              :payload filtered-acl
              :on-done (fn [{:keys [success? get-parsed-response]}]
                         (swap! state dissoc :saving?)
                         (if success?
                           (do
                             ((:update-owners props) (map :email (filter #(= "OWNER" (:accessLevel %)) filtered-acl)))
                             ((:dismiss-self props)))
                           (swap! state assoc :save-error (get-parsed-response))))})))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (common/scroll-to-top 100)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace-acl (:workspace-id props))
        :on-done
        (fn [{:keys [success? get-parsed-response status-text]}]
          (if success?
            (swap! state assoc :acl-vec
                   (mapv (fn [[k v]]
                           {:email k :accessLevel v :read-only? true})
                         (get-parsed-response)))
            (swap! state assoc :load-error (get-parsed-response))))}))})
