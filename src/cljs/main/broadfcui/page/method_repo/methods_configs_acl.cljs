(ns broadfcui.page.method-repo.methods-configs-acl
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.style :as style]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.net :as net]
   [broadfcui.utils :as utils]
   ))


(defn get-ordered-name [entity]
  (clojure.string/join ":" (replace entity [:namespace :name :snapshotId])))

(def ^:private reader-level "READER")
(def ^:private owner-level "OWNER")
(def ^:private no-access-level "NO ACCESS")
(def ^:private access-levels [reader-level owner-level no-access-level])

(def ^:private column-width "calc(50% - 4px)")

(react/defc AgoraPermsEditor
  {:render
   (fn [{:keys [props state this]}]
     [modals/OKCancelForm
      {:header (str "Permissions for " (:title props))
       :dismiss (:dismiss props)
       :content
       (react/create-element
        (net/render-with-ajax
         (:acl-response @state)
         #(this :-render-acl-form)
         {:loading-text (str "Loading Permissions for " (:title props) "...")
          :rephrase-error
          #(if (= 403 (:status-code %))
             (str "You are unauthorized to edit this "
                  (clojure.string/lower-case (:entityType props)) ".")
             (get-in % [:parsed-response :message]))}))
       :ok-button (when (:acl-vec @state) {:text "Save" :onClick #(this :-persist-acl)})}])
   :component-did-mount
   (fn [{:keys [props state locals]}]
     (endpoints/call-ajax-orch
      {:endpoint (:load-endpoint props)
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? parsed-response] :as response}]
                   (swap! state assoc :acl-response response)
                   (when success?
                     (let [acl-vec (filterv #(not= "public" (:user %)) parsed-response)
                           public-user (first (filter #(= "public" (:user %)) parsed-response))
                           public-status (or (:role public-user) no-access-level)]
                       (when (= "Configuration" (:entityType props))
                         (swap! locals assoc :initial-users (set (map :user acl-vec))))
                       (swap! state assoc :acl-vec acl-vec
                              :public-status (= public-status reader-level)
                              :count-orig (count acl-vec))))))}))
   :-render-acl-form
   (fn [{:keys [state this]}]
     (let [{:keys [acl-vec public-status count-orig]} @state]
       [:div {:style {:width 800}}
        (when (:saving? @state)
          [comps/Blocker {:banner "Updating..."}])
        [:div {:style {:paddingBottom "0.5em" :fontSize "90%"}}
         [:div {:style {:float "left" :width column-width}} "User or Group ID"]
         [:div {:style {:float "right" :width column-width}} "Access Level"]
         (common/clear-both)]
        (map-indexed
         (fn [i acl-entry]
           [:div {}
            [input/TextField
             {:ref (str "acl-key" i)
              :style {:float "left" :width column-width
                      :backgroundColor (when (< i count-orig)
                                         (:background-light style/colors))}
              :disabled (< i count-orig)
              :spellCheck false
              :defaultValue (:user acl-entry)
              :predicates [(input/valid-email-or-empty "User ID")]}]
            (let [disabled? (= (utils/get-user-email) (:user acl-entry))]
              (style/create-identity-select
               {:ref (str "acl-value" i)
                :style {:float "right" :width column-width :height 33}
                :disabled disabled?
                :title (when disabled? "You cannot edit your own permissions.")
                :defaultValue (:role acl-entry)}
               access-levels))
            (common/clear-both)])
         acl-vec)
        [comps/Button {:text "Add new" :icon :add-new
                       :onClick #(swap! state assoc :acl-vec
                                        (conj (this :-capture-ui-state)
                                              {:user "" :role reader-level}))}]
        [:label {:style {:cursor "pointer"}}
         [:input {:type "checkbox"
                  :style {:marginLeft "2em" :verticalAlign "middle"}
                  :onChange #(swap! state assoc :public-status (.. % -target -checked))
                  :checked public-status}]
         [:span {:style {:paddingLeft 6 :verticalAlign "middle"}} "Publicly Readable?"]]
        (style/create-server-error-message (:persist-error @state))]))
   :-persist-acl
   (fn [{:keys [props state refs this]}]
     (swap! state dissoc :validation-error :save-error)
     (let [acl-vec (this :-capture-ui-state)
           failure (apply input/validate refs (map #(str "acl-key" %) (range (count acl-vec))))]
       (if failure
         (swap! state assoc :persist-error failure)
         (let [non-empty-acls (filterv #(not (empty? (:user %))) acl-vec)
               non-empty-acls-w-public (conj non-empty-acls
                                             {:user "public" :role
                                              (if (:public-status @state) reader-level no-access-level)})]
           (swap! state assoc :saving? true)
           (endpoints/call-ajax-orch
            {:endpoint (:save-endpoint props)
             :headers utils/content-type=json
             :payload non-empty-acls-w-public
             :on-done (net/handle-ajax-response
                       (fn [{:keys [success? parsed-response]}]
                         (if success?
                           (this :-post-update (set (map :user non-empty-acls)))
                           (swap! state assoc :persist-error (:message parsed-response) :saving? nil))))})))))
   :-capture-ui-state
   (fn [{:keys [state refs]}]
     (mapv
      (fn [i]
        {:user (input/get-text refs (str "acl-key" i))
         :role (.-value (@refs (str "acl-value" i)))})
      (range (count (:acl-vec @state)))))
   :-post-update
   (fn [{:keys [props locals]} new-users]
     (when (= "Configuration" (:entityType props))
       (let [diff (set/difference new-users (:initial-users @locals))]
         (when (seq diff)
           ((:on-users-added props) diff))))
     ((:dismiss props)))})
