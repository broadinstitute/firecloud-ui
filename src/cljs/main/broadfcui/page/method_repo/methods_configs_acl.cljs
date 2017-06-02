(ns broadfcui.page.method-repo.methods-configs-acl
  (:require
   [dmohs.react :as react]
   [clojure.string :refer [trim]]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   [broadfcui.net :as net]
   ))


(defn- get-ordered-name [entity]
  (clojure.string/join ":" (replace entity [:namespace :name :snapshotId])))

(def ^:private reader-level "READER")
(def ^:private owner-level "OWNER")
(def ^:private no-access-level "NO ACCESS")
(def ^:private access-levels [reader-level owner-level no-access-level])

(def ^:private column-width "calc(50% - 4px)")
;;:on-done (fn [{:keys [success? get-parsed-response status-text xhr]}]
       ;;           (if success?
       ;;             (let [response-vec (mapv utils/keywordize-keys (get-parsed-response false))
       ;;                   acl-vec (filterv #(not= "public" (:user %)) response-vec)
       ;;                   public-user (first (filter #(= "public" (:user %)) response-vec))
       ;;                   public-status (or (:role public-user) no-access-level)]
       ;;               (swap! state assoc :acl-vec acl-vec
       ;;                      :public-status (= public-status reader-level)
       ;;                      :count-orig (count acl-vec)))
       ;;             )
       ;;           (net/create-handle-ajax-response state :acl-response)
       ;;           (utils/log "after create-handle"))}))
(defn render-ok-cancel-form [props state refs this]
  (let [acl-vec (filterv #(not= "public" (:user %)) (get-in state [:acl-response :parsed]))
        public-status (as-> (get-in state [:acl-response :parsed]) rv
                             (filter #(= "public" (:user %)) rv)
                             (first rv)
                             (or (:role rv) no-access-level))]
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
                  :backgroundColor (when (< i (count acl-vec))
                                     (:background-light style/colors))}
          :disabled (< i (count acl-vec))
          :spellCheck false
          :defaultValue (:user acl-entry)
          :predicates [(input/valid-email-or-empty "User ID")]}]
        (style/create-identity-select
         {:ref (str "acl-value" i)
          :style {:float "right" :width column-width :height 33}
          :defaultValue (:role acl-entry)}
         access-levels)
        (common/clear-both)])
     (acl-vec))
    [comps/Button {:text "Add new" :icon :add-new
                   :onClick #(swap! state assoc :acl-vec
                                    (conj (react/call :capture-ui-state this)
                                          {:user "" :role reader-level}))}]
    [:label {:style {:cursor "pointer"}}
     [:input {:type "checkbox" :ref "publicbox"
              :style {:marginLeft "2em" :verticalAlign "middle"}
              :onChange #(swap! state assoc :public-status (-> (@refs "publicbox") .-checked))
              :checked (:public-status @state)}]
     [:span {:style {:paddingLeft 6 :verticalAlign "middle"}} "Publicly Readable?"]]]))

(react/defc AgoraPermsEditor
  {:render
   (fn [{:keys [props refs state this]}]
     [comps/OKCancelForm
      {:header (str "Permissions for " (:title props))
       :content
       (react/create-element
        (net/render-with-ajax
         (:acl-response @state)
         #(render-ok-cancel-form props refs state this)
         {:loading-text (str "Loading Permissions for " (:title props) "...")
          :error-override
          (partial net/overwrite-error (str "You are unauthorized to edit this " (clojure.string/lower-case (:entityType props)) ".") 403)}))
       :ok-button (when (:acl-vec @state) {:text "Save" :onClick #(react/call :persist-acl this)})}])
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (:load-endpoint props)
       :on-done (net/handle-ajax-response
                 (fn [k v] (swap! state assoc-in [:acl-response k] v)))}))
   :persist-acl
   (fn [{:keys [props state refs this]}]
     (swap! state dissoc :validation-error :save-error)
     (let [acl-vec (react/call :capture-ui-state this)
           failure (apply input/validate refs (map #(str "acl-key" %) (range (count acl-vec))))]
       (if failure
         (swap! state assoc :validation-error failure)
         (let [non-empty-acls (filterv #(not (empty? (:user %))) acl-vec)
               non-empty-acls-w-public (conj non-empty-acls
                                             {:user "public" :role
                                              (if (:public-status @state) reader-level no-access-level)})]
           (swap! state assoc :saving? true)
           (endpoints/call-ajax-orch
            {:endpoint (:save-endpoint props)
             :headers utils/content-type=json
             :payload non-empty-acls-w-public
             :on-done (fn [{:keys [success? get-parsed-response]}]
                        (swap! state dissoc :saving?)
                        (if success?
                          (modal/pop-modal)
                          (swap! state assoc :save-error (get-parsed-response false))))})))))
   :capture-ui-state
   (fn [{:keys [state refs]}]
     (mapv
      (fn [i]
        {:user (input/get-text refs (str "acl-key" i))
         :role (.-value (@refs (str "acl-value" i)))})
      (range (count (:acl-vec @state)))))})
