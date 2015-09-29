(ns org.broadinstitute.firecloud-ui.page.methods-configs-acl
  (:require
   [dmohs.react :as react]
   [clojure.string :refer [trim]]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
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

(defn- is-conf? [e]
  (contains? e "method"))

(def ^:private access-levels
  ["READER" "OWNER" "NO ACCESS"])

(defn- build-acl-vec [acl-map]
  (mapv
    (fn [k] {:userId k :accessLevel (acl-map k)})
    (keys acl-map)))


(defn- ui-acl-to-agora-perms [ui-acl-str]
  (cond
    (= "NO ACCESS" ui-acl-str) [] ; empty array for no access
    (= "READER" ui-acl-str) ["Read"] ; just reader
    (= "OWNER" ui-acl-str) ["Read" "Write" "Create" "Redact" "Manage"])) ; all 5 perms

(defn- translate-single-acl-map [a-map]
  (let [ui-role (a-map :accessLevel)
        agora-arr (ui-acl-to-agora-perms ui-role)
        new-map (merge a-map {:accessLevel agora-arr})]
    (utils/rlog (str "the ui-role is " ui-role))
        new-map))

(defn- agora-perms-to-ui-acl [agora_perm_arr]
  (let [get-user (fn [e] (e "user"))
        get-roles (fn [e] (e "roles"))
        map-role-arr-to-ui-role (fn [role-arr]
                    (cond
                      (= 0 (count role-arr))
                      "NO ACCESS" ; no entries maps to "NO ACCESS"
                      (and (= 1 (count role-arr)) (= "Read" (nth role-arr 0)))
                      "READER" ;exactly one entriy 'Read' maps to "READER"
                      (= 5 (count role-arr))
                      "OWNER" ; 5 entries maps to "OWNER"
                      :else "READER"))
        mapping-func (fn [perm-item]
                       (let [user (get-user perm-item)
                             roles (get-roles perm-item)
                             ui-role (map-role-arr-to-ui-role roles)]
                         {user ui-role}))]
    (map mapping-func agora_perm_arr)))

(def ^:private column-width "calc(50% - 4px)")

(react/defc AgoraPermsEditor
  {:render
   (fn [{:keys [props state this]}]
     [comps/Dialog
      {:width "75%"
       :blocking? true
       :dismiss-self (:dismiss-self props)
       :content (react/create-element
                  [:div {:style {:background "#fff" :padding "2em"}}
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
                              :defaultValue (:userId acl-entry)})
                           (style/create-select
                             {:ref (str "acl-value" i)
                              :style {:float "right" :width column-width :height 33}
                              :defaultValue (:accessLevel acl-entry)}
                             access-levels)
                           (common/clear-both)])
                        (:acl-vec @state))
                      [comps/Button {:text "Add new" :style :add
                                     :onClick #(swap! state assoc :acl-vec
                                                (conj
                                                  (react/call :capture-ui-state this)
                                                  {:userId "" :accessLevel "READER"}))}]
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
                     (let [ui-maps (agora-perms-to-ui-acl (get-parsed-response))
                           acl-vec (build-acl-vec (reduce merge ui-maps))]
                       (swap! state assoc :acl-vec acl-vec :count-orig (count acl-vec)))
                     (swap! state assoc :error status-text)))}))
   :persist-acl
   (fn [{:keys [props state this]}]
     (swap! state assoc :saving? true)
     (swap! state assoc :acl-vec (map translate-single-acl-map
                                   (react/call :capture-ui-state this)))
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/persist-agora-method-acl (:selected-entity props))
        :headers {"Content-Type" "application/json"}
        :payload (filter #(not (empty? (:userId %))) (:acl-vec @state))
        :on-done (fn [{:keys [success? status-text]}]
                   (swap! state dissoc :saving?)
                   (if success?
                     ((:dismiss-self props))
                     (js/alert "Error saving permissions: " status-text)))}))
   :capture-ui-state
   (fn [{:keys [state refs]}]
     (mapv
       (fn [i]
         {:userId (-> (@refs (str "acl-key" i)) .getDOMNode .-value trim)
          :accessLevel (-> (@refs (str "acl-value" i)) .getDOMNode .-value)})
       (range (count (:acl-vec @state)))))})