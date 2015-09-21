(ns org.broadinstitute.firecloud-ui.page.workspace.summary-tab
  (:require
    [clojure.string :refer [trim capitalize]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- render-tags [tags]
  (let [tagstyle {:marginRight 13 :borderRadius 2 :padding "5px 13px"
                  :backgroundColor (:tag-background style/colors)
                  :color (:tag-foreground style/colors)
                  :display "inline-block" :fontSize "94%"}]
    [:div {}
     (map (fn [tag] [:span {:style tagstyle} tag]) tags)]))

(def ^:private access-levels
  ["OWNER" "WRITER" "READER" "NO ACCESS"])

(def ^:private column-width "calc(50% - 4px)")

(defn- build-acl-vec [acl-map]
  (mapv
    (fn [k] {:userId k :accessLevel (acl-map k)})
    (keys acl-map)))

(react/defc AclEditor
  {:render
   (fn [{:keys [props state this]}]
     [comps/Dialog
      {:width "50%"
       :dismiss-self (:dismiss-self props)
       :content
       (react/create-element
         [:div {:style {:background "#fff" :padding "2em"}}
          (cond
            (:acl-vec @state)
            [:div {}
             (when (:saving? @state)
               [comps/Blocker {:banner "Updating..."}])
             [:div {:style {:paddingBottom "0.5em" :fontSize "90%"}}
              [:h4 {} (let [workspace-id (:workspace-id props)
                            ws-name (:name workspace-id)
                            ws-ns (:namespace workspace-id)]
                        (str "Permisssions for "
                          (clojure.string/join ":" [ws-ns ws-name])))]
              [:div {:style {:float "left" :width column-width}} "User or Group ID"]
              [:div {:style {:float "right" :width column-width}} "Access Level"]
              (common/clear-both)]
             (map-indexed
               (fn [i acl-entry]
                 [:div {}
                  (style/create-text-field
                    {:ref (str "acl-key" i)
                     :style {:float "left" :width column-width
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
                                       (swap! state update-in [:acl-vec] conj {:userId "" :accessLevel "READER"}))}]
             [:div {:style {:textAlign "center" :marginTop "1em"}}
              [:a {:href "javascript:;"
                   :style {:textDecoration "none" :color (:button-blue style/colors) :marginRight "1.5em"}
                   :onClick #((:dismiss-self props))}
               "Cancel"]
              [comps/Button {:text "Save"
                             :onClick #(do
                                        (react/call :capture-ui-state this)
                                        (react/call :persist-acl this))}]]]

            (:error @state) (style/create-server-error-message (:error @state))
            :else [comps/Spinner {:text "Loading Permissions..."}])])}])
   :capture-ui-state
   (fn [{:keys [state refs]}]
     (swap! state assoc :acl-vec
       (mapv
         (fn [i]
           {:userId (-> (@refs (str "acl-key" i)) .getDOMNode .-value trim)
            :accessLevel (-> (@refs (str "acl-value" i)) .getDOMNode .-value)})
         (range (count (:acl-vec @state))))))
   :persist-acl
   (fn [{:keys [props state]}]
     (swap! state assoc :saving? true)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/update-workspace-acl (:workspace-id props))
        :headers {"Content-Type" "application/json"}
        :payload (filter #(not (empty? (:userId %))) (:acl-vec @state))
        :on-done (fn [{:keys [success? status-text]}]
                   (swap! state dissoc :saving?)
                   (if success?
                     ((:dismiss-self props))
                     (js/alert "Error saving permissions: " status-text)))}))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace-acl (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (let [acl-vec (build-acl-vec ((get-parsed-response) "acl"))]
                       (swap! state assoc :acl-vec acl-vec :count-orig (count acl-vec)))
                     (swap! state assoc :error status-text)))}))})

(react/defc Summary
  {:render
   (fn [{:keys [state props this]}]
     (cond
       (nil? (:server-response @state))
       [comps/Spinner {:text "Loading workspace..."}]
       (get-in @state [:server-response :error-message])
       (style/create-server-error-message (get-in @state [:server-response :error-message]))
       :else
       (let [ws (get-in @state [:server-response :workspace])
             owner? (= "OWNER" (ws "accessLevel"))
             status (common/compute-status ws)]
         [:div {:style {:margin "45px 25px"}}
          (when (:deleting? @state)
            [comps/Blocker {:banner "Deleting..."}])
          (when (:editing-acl? @state)
            [AclEditor {:workspace-id (:workspace-id props)
                        :dismiss-self #(swap! state dissoc :editing-acl?)}])
          [:div {:style {:float "left" :width 290 :marginRight 40}}
           ;; TODO - make the width of the float-left dynamic
           [comps/StatusLabel {:text (capitalize (name status))
                               :icon (case status
                                       "Complete" [comps/CompleteIcon {:size 36}]
                                       "Running" [comps/RunningIcon {:size 36}]
                                       "Exception" [comps/ExceptionIcon {:size 36}])
                               :color (style/color-for-status status)}]
           [comps/SidebarButton {:style :light :margin :top :color :button-blue
                                 :text "Edit this page" :icon :pencil
                                 :onClick #(utils/rlog "TODO: implement edit")}]
           (when owner?
             [comps/SidebarButton {:style :light :margin :top :color :exception-red
                                   :text "Delete" :icon :trash-can
                                   :onClick #(when (js/confirm "Are you sure?")
                                              (swap! state assoc :deleting? true)
                                              (react/call :delete this))}])]
          [:div {:style {:display "inline-block"}}
           (style/create-section-header "Workspace Owner")
           (style/create-paragraph
             [:div {} [:strong {} (clojure.string/join ", " (ws "owners"))]
              (when owner?
                [:span {}
                 " ("
                 [:a {:href "javascript:;"
                      :style {:color (:button-blue style/colors) :textDecoration "none"}
                      :onClick #(swap! state assoc :editing-acl? true)}
                  "Edit sharing"]
                 ")"])])
           (style/create-section-header "Description")
           (style/create-paragraph [:em {} "Description info not available yet"])
           (style/create-section-header "Tags")
           (style/create-paragraph (render-tags ["Fake" "Tag" "Placeholders"]))
           (style/create-section-header "Research Purpose")
           (style/create-paragraph [:em {} "Research purpose not available yet"])
           (style/create-section-header "Billing Account")
           (style/create-paragraph [:em {} "Billing account not available yet"])]
          [:div {:style {:clear "both"}}]])))
   :load-workspace
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (swap! state assoc :server-response
                     (if success? {:workspace (get-parsed-response)}
                                  {:error-message status-text})))}))
   :delete
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/delete-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? status-text]}]
                   (swap! state dissoc :deleting?)
                   (if success?
                     ((:on-delete props))
                     (js/alert (str "Error on delete: " status-text))))}))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-workspace this))
   :component-did-update
   (fn [{:keys [this state]}]
     (when (nil? (:server-response @state))
       (react/call :load-workspace this)))
   :component-will-receive-props
   (fn [{:keys [props next-props state]}]
     (utils/cljslog props next-props)
     (when-not (apply = (map :workspace-id [props next-props]))
       (swap! state assoc :server-response nil)))})


(defn render [workspace-id on-delete]
  (react/create-element Summary {:workspace-id workspace-id :on-delete on-delete}))
