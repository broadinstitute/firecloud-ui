(ns org.broadinstitute.firecloud-ui.page.method-repo.methods-configs-acl
  (:require
   [dmohs.react :as react]
   [clojure.string :refer [trim]]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.input :as input]
   [org.broadinstitute.firecloud-ui.common.modal :as modal]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(defn- get-ordered-name [entity]
  (clojure.string/join ":"
    [(entity "namespace")
     (entity "name")
     (entity "snapshotId")]))

(def ^:private reader-level "READER")
(def ^:private owner-level "OWNER")
(def ^:private no-access-level "NO ACCESS")
(def ^:private access-levels [reader-level owner-level no-access-level])

(def ^:private column-width "calc(50% - 4px)")

(defn count-owners [acl-vec]
  (count (filter #(= (:role %) owner-level) acl-vec)))

(react/defc AgoraPermsEditor
  {:render
   (fn [{:keys [props refs state this]}]
     [modal/OKCancelForm
      {:header (str "Permissions for " (:title props))
       :content
       (react/create-element
        (cond
          (:acl-vec @state)
          [:div {:style {:width 800}}
           (when (:saving? @state)
             [comps/Blocker {:banner "Updating..."}])
           [:div {:style {:paddingBottom "0.5em" :fontSize "90%"}}
            [:div {:style {:float "left" :width column-width}} "User or Group ID"]
            [:div {:style {:float "right" :width column-width}} "Access Level"]
            (common/clear-both)]
           (map-indexed
            (fn [i acl-entry]
              (let [disabled? (and (< (count-owners(:acl-vec @state)) 2) (= owner-level (:role acl-entry)))
                    background (if disabled? (:disabled-state style/colors) (:input-background style/colors))]
                [:div {}
                 [input/TextField
                  {:ref (str "acl-key" i)
                   :style {:float "left" :width column-width
                           :backgroundColor (when (< i (:count-orig @state))
                                            (:background-light style/colors))}
                   :disabled (< i (:count-orig @state))
                   :spellCheck false
                   :defaultValue (:user acl-entry)
                   :predicates [(input/valid-email-or-empty "User ID")]}]
                   (style/create-identity-select
                     (merge {:ref (str "acl-value" i)
                             :style {:float "right" :width column-width :height 33 :backgroundColor background}
                             :defaultValue (:role acl-entry)}
                       (when disabled? {:disabled true}))
                     access-levels)
                  (common/clear-both)]))
            (:acl-vec @state))
           [comps/Button {:text "Add new" :icon :add
                          :onClick #(swap! state assoc :acl-vec
                                           (conj (react/call :capture-ui-state this)
                                                 {:user "" :role reader-level}))}]
           [:label {:style {:cursor "pointer"}}
            [:input {:type "checkbox" :ref "publicbox"
                     :style {:marginLeft "2em" :verticalAlign "middle"}
                     :onChange #(swap! state assoc :public-status (-> (@refs "publicbox") .-checked))
                     :checked (:public-status @state)}]
            [:span {:style {:paddingLeft 6 :verticalAlign "middle"}} "Publicly Readable?"]]
           (style/create-validation-error-message (:validation-error @state))
           [comps/ErrorViewer {:error (:save-error @state)}]]
          (:error @state) (style/create-server-error-message (cond (= (:error @state) "Forbidden") (str "You are unauthorized to edit this " (clojure.string/lower-case (:entityType props)) ".")
                                                                   :else (:error @state)))
          :else [comps/Spinner {:text
                                (str "Loading Permissions for " (:title props) "...")}]))
       :ok-button (when (:acl-vec @state) {:text "Save" :onClick #(react/call :persist-acl this)})}])
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (:load-endpoint props)
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (let [response-vec (mapv utils/keywordize-keys (get-parsed-response false))
                          acl-vec (filterv #(not= "public" (:user %)) response-vec)
                          public-user (first (filter #(= "public" (:user %)) response-vec))
                          public-status (or (:role public-user) no-access-level)]
                      (swap! state assoc :acl-vec acl-vec
                             :public-status (= public-status reader-level)
                             :count-orig (count acl-vec)))
                    (swap! state assoc :error status-text)))}))
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
