(ns org.broadinstitute.firecloud-ui.page.workspace.summary.acl-editor
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    ))

(def ^:private access-levels
  ["OWNER" "WRITER" "READER" "NO ACCESS"])

(def ^:private column-width "calc(50% - 4px)")

(defn- build-acl-vec [acl-map]
  (mapv
    (fn [k] {:userId k :accessLevel (acl-map k)})
    (keys acl-map)))

(defn- render-acl-content [props state this]
  [comps/OKCancelForm
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
            (style/create-text-field
              {:ref (str "acl-key" i)
               :style {:float "left" :width column-width :color "black"
                       :backgroundColor (when (< i (:count-orig @state)) (:background-gray style/colors))}
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
                      :onClick #(do
                                 (react/call :capture-ui-state this)
                                 (swap! state update-in [:acl-vec] conj {:userId "" :accessLevel "READER"}))}]])
    :dismiss-self (:dismiss-self props)
    :ok-button
    [comps/Button {:text "Save"
                   :onClick #(do
                              (react/call :capture-ui-state this)
                              (react/call :persist-acl this))}]}])

(react/defc AclEditor
  {:render
   (fn [{:keys [props state this]}]
     [comps/Dialog
      {:width "50%"
       :dismiss-self (:dismiss-self props)
       :content
       (react/create-element
         (if (:acl-vec @state)
           (render-acl-content props state this)
           [:div {:style {:padding "2em"}}
            (if (:error @state)
              (style/create-server-error-message (:error @state))
              [comps/Spinner {:text "Loading Permissions..."}])]))}])
   :capture-ui-state
   (fn [{:keys [state refs]}]
     (swap! state assoc :acl-vec
       (mapv
         (fn [i]
           (let [[userId accessLevel] (common/get-text refs (str "acl-key" i) (str "acl-value" i))]
             {:userId userId :accessLevel accessLevel}))
         (range (count (:acl-vec @state))))))
   :persist-acl
   (fn [{:keys [props state]}]
     (swap! state assoc :saving? true)
     (let [filtered-acl (filter #(not (empty? (:userId %))) (:acl-vec @state))]
       (endpoints/call-ajax-orch
         {:endpoint (endpoints/update-workspace-acl (:workspace-id props))
          :headers {"Content-Type" "application/json"}
          :payload filtered-acl
          :on-done (fn [{:keys [success? status-text]}]
                     (swap! state dissoc :saving?)
                     (if success?
                       (do
                         ((:update-owners props) (map :userId (filter #(= "OWNER" (:accessLevel %)) filtered-acl)))
                         ((:dismiss-self props)))
                       (js/alert "Error saving permissions: " status-text)))})))
   :component-did-mount
   (fn [{:keys [props state]}]
     (common/scroll-to-top 100)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace-acl (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (let [acl-vec (build-acl-vec ((get-parsed-response) "acl"))]
                       (swap! state assoc :acl-vec acl-vec :count-orig (count acl-vec)))
                     (swap! state assoc :error status-text)))}))})