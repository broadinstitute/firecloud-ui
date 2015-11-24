(ns org.broadinstitute.firecloud-ui.page.methods-configs-acl
  (:require
   [dmohs.react :as react]
   [clojure.string :refer [trim]]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.common.table :as table]
   [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(defn- get-ordered-name [entity]
  (clojure.string/join ":"
    [(entity "namespace")
     (entity "name")
     (entity "snapshotId")]))

(def ^:private access-levels
  ["READER" "OWNER" "NO ACCESS"])

(def ^:private reader-level (nth access-levels 0))
(def ^:private owner-level (nth access-levels 1))
(def ^:private no-access-level (nth access-levels 2))

(defn- index-to-access-level [idx]
  (nth access-levels idx))

(defn- access-level-to-index [access-level]
  (cond
    (= "READER" access-level) 0
    (= "OWNER" access-level) 1
    (= "NO ACCESS" access-level) 2
    :else (do  (utils/log (str "Unknown access-level :'" access-level "'"))  reader-level)))


(def ^:private column-width "calc(50% - 4px)")

(defn- correspondsToReader [access-level]
    (or
      (= access-level reader-level)
      (= access-level owner-level)))




(defn- validate-user [acl-map]
  (let [user (:user acl-map)]
    (not (= "public" user))))


(defn- filter-public [acl-vec]
  (filter validate-user acl-vec))


(defn- make-ui-vec [a-map]
  {:user (get a-map "user")
   :role (get a-map "role")})

(defn- extract-last-public-access-level [acl-vec]

  (let [hasPublicUser
        (fn [m]
          (= "public" (:user m)))
        justPublic (filter hasPublicUser acl-vec)
        numJustPublic (count justPublic)]
      (if (<= numJustPublic 0)
      ;if public isn't in the acl-return NO ACCESS
      no-access-level
      (let [lastPublic (nth justPublic (- numJustPublic 1))
            lastPublicAccessLevel (get lastPublic :role)]
        ;if public is in the acl return the value of the last one
        lastPublicAccessLevel))))



(react/defc AgoraPermsEditor
  {:render
   (fn [{:keys [props refs state this]}]
     [dialog/Dialog
      {:width "75%"
       :blocking? true
       :dismiss-self (:dismiss-self props)
       :content (react/create-element
                  [:div {:style {:background "#fff" :padding "2em"}}
                   [comps/XButton {:dismiss (:dismiss-self props)}]
                   (cond
                     (:acl-vec @state)
                     [:div {}
                      (when (:saving? @state)
                        [comps/Blocker {:banner "Updating..."}])
                      [:div {:style {:paddingBottom "0.5em" :fontSize "90%"}}
                       [:h4 {} (let [sel-ent (:selected-entity props)
                                     ent-type (sel-ent "entityType")
                                     disp (get-ordered-name sel-ent)]
                                 (str "Permissions for " ent-type " " disp))]
                       [:div {:style
                              {:float "left" :width column-width}}
                        "User or Group ID"]
                       [:div {:style
                              {:float "right" :width column-width}}
                        "Access Level"]
                       (common/clear-both)]
                      (map-indexed
                        (fn [i acl-entry]
                          [:div {}
                           (style/create-text-field
                             {:ref (str "acl-key" i)
                              :style {:float "left" :width column-width
                                      :backgroundColor (when (< i (:count-orig @state))
                                                         (:background-gray style/colors))}
                              :disabled (< i (:count-orig @state))
                              :spellCheck false
                              :defaultValue (:user acl-entry)})
                           (style/create-select
                             {:ref (str "acl-value" i)
                              :style {:float "right" :width column-width :height 33}
                              :defaultValue (access-level-to-index (:role acl-entry))}
                             access-levels)
                           (common/clear-both)])
                        (:acl-vec @state))
                      [comps/Button {:text "Add new" :style :add
                                     :onClick #(swap! state assoc :acl-vec
                                                (conj
                                                  (react/call :capture-ui-state this)
                                                  {:user "" :role reader-level}))}]
                      [:input {:type "checkbox"
                               :ref "publicbox"
                               :onChange (fn []
                                           (let [checkValue (-> (@refs "publicbox") .getDOMNode .-checked)]
                                             (swap! state assoc :public-status checkValue)))
                               :checked (:public-status @state)}]
                      "Publicly Readable?"
                      [:div {:style {:textAlign "center" :marginTop "1em"}}
                       [:a {:href "javascript:;"
                            :style {:textDecoration "none"
                                    :color (:button-blue style/colors)
                                    :marginRight "1.5em"}
                            :onClick #((:dismiss-self props))}
                        "Cancel"]
                       [comps/Button {:text "Save"
                                      :onClick #(react/call :persist-acl this)}]]]
                     (:error @state) (style/create-server-error-message (:error @state))
                     :else [comps/Spinner {:text
                                           (str "Loading Permissions for "
                                             ((:selected-entity props) "entityType") " "
                                             (get-ordered-name (:selected-entity props))
                                             "...")}])])}])
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (let [ent (:selected-entity props)
                        name (ent "name")
                        nmsp (ent "namespace")
                        sid (ent "snapshotId")]
                    (endpoints/get-agora-method-acl
                      nmsp name sid (:is-conf props)))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (let [parsed-response (get-parsed-response)
                           ui-vec (mapv make-ui-vec parsed-response)
                           public-level (extract-last-public-access-level ui-vec)
                           checkValue (correspondsToReader public-level)
                           filtered-ui-vec (filter-public ui-vec)
                           acl-vec filtered-ui-vec]
                       (swap! state assoc :acl-vec acl-vec
                         :public-status checkValue
                         :count-orig (count acl-vec)))
                     (swap! state assoc :error status-text)))}))
   :persist-acl
   (fn [{:keys [props state this]}]
     (swap! state assoc :acl-vec (react/call :capture-ui-state this))
     (let [validity-flags (map validate-user (:acl-vec @state))
           all-valid (every? true? validity-flags)]
       (if all-valid
         (let
           [non-empty-acls (filterv #(not (empty? (:user %))) (:acl-vec @state))
            non-empty-acls-w-public (flatten [non-empty-acls
                                              {:user "public" :role
                                               (if (:public-status @state)
                                                 reader-level no-access-level)}])]
           (swap! state assoc :saving? true)
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/persist-agora-method-acl (:selected-entity props))
              :headers {"Content-Type" "application/json"}
              :payload non-empty-acls-w-public
              :on-done (fn [{:keys [success? status-text]}]
                         (swap! state dissoc :saving?)
                         (if success?
                           ((:dismiss-self props))
                           (js/alert "Error saving permissions: " status-text)))}))
         (js/alert "Cannot set value to 'public'!  Use the check-box instead."))))
   :capture-ui-state
   (fn [{:keys [state refs]}]
     (mapv
       (fn [i]
         {:user (-> (@refs (str "acl-key" i)) .getDOMNode .-value trim)
          :role (index-to-access-level (int (-> (@refs (str "acl-value" i)) .getDOMNode .-value)))})
       (range (count (:acl-vec @state)))))})
