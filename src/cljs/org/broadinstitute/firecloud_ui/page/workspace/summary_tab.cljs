(ns org.broadinstitute.firecloud-ui.page.workspace.summary-tab
  (:require
    [clojure.string :refer [trim capitalize blank?]]
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

(defn- render-acl-content [props state this]
  [comps/OKCancelForm
   {:header
    (let [workspace-id (:workspace-id props)]
      (str "Permissions for " (:namespace workspace-id) ":" (:name workspace-id)))
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

(defn- create-section [& children]
  [:div {:style {:padding "1em 0 2em 0"}} children])

(defn- reload-attributes [props state on-success]
  (endpoints/call-ajax-orch
    {:endpoint (endpoints/get-workspace (:workspace-id props))
     :on-done  (fn [{:keys [success? get-parsed-response status-text]}]
                 (if success?
                   (let [response (get-parsed-response)
                         ws (response "workspace")
                         attrs-list (mapv (fn [[k v]] [k v]) (ws "attributes"))]
                     (swap! state assoc :server-response
                       {:attrs-list attrs-list} :loaded-attrs? true :saving? true)
                     (on-success))
                   (swap! state assoc :server-response {:error-message status-text})))}))

(defn- add-update-attributes [props attrs on-success]
  (let [workspace-id (:workspace-id props)
        add-update-ops (for [pair attrs]
                         {:op "AddUpdateAttribute"
                          :attributeName (key pair)
                          :addUpdateAttribute (val pair)})]
    (endpoints/call-ajax-orch
      {:endpoint (endpoints/update-workspace-attrs workspace-id)
       :method :PATCH
       :payload add-update-ops
       :headers {"Content-Type" "application/json"}
       :on-done (fn [{:keys [success? xhr]}]
                  (if-not success?
                    (js/alert (str "Exception:\n" (.-statusText xhr)))
                    (on-success)))
       :canned-response {:status 200 :delay-ms (rand-int 2000)}})))

(defn- delete-attribute [props attr on-success]
  (let [workspace-id (:workspace-id props)
        delete-op [{:op "RemoveAttribute"
                    :attributeName attr}]]
    (endpoints/call-ajax-orch
      {:endpoint (endpoints/update-workspace-attrs workspace-id)
       :method :PATCH
       :payload delete-op
       :headers {"Content-Type" "application/json"}
       :on-done (fn [{:keys [success? xhr]}]
                  (if-not success?
                    (js/alert (str "Exception:\n" (.-statusText xhr)))
                    (on-success)))
       :canned-response {:status 200 :delay-ms (rand-int 2000)}})))

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
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace-acl (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (let [acl-vec (build-acl-vec ((get-parsed-response) "acl"))]
                       (swap! state assoc :acl-vec acl-vec :count-orig (count acl-vec)))
                     (swap! state assoc :error status-text)))}))})

(react/defc AttributeViewer
  {:get-initial-state
   (fn [{:keys [props]}]
     {:editing? false :attrs-list (:attrs-list props)})
   :render
   (fn [{:keys [this props state]}]
     (let [{:keys [ws writer? on-done attrs-list]} props]
       [:div {:style {:margin "45px 25px"}}
        (react/call :render-sidebar this)
        [:div {:style {:display "inline-block"}}
         [:div {:style {:display "inline-block"}}
          (style/create-section-header "Workspace Attributes")
          (create-section
            (when (or (:saving? @state) (:deleting? @state))
              [comps/Blocker {:banner "Updating..."}])
            [:div {}
             (map-indexed
               (fn [i a]
                 [:div {}
                  [:div {:style {:float "left" :marginRight "0.5em"}}
                   (style/create-text-field
                     {:value (first a)
                      :onChange #(swap! state update-in [:attrs-list i]
                                  assoc 0 (-> % .-target .-value))
                      :disabled (or (not (:editing? @state))
                                  (contains? (:reserved-keys @state) i))
                      :style (if (or (contains? (:reserved-keys @state) i)
                                   (not (:editing? @state)))
                               {:backgroundColor (:background-gray style/colors)}
                               {:backgroundColor "#fff"})})]
                  [:div {:style {:float "right"}}
                   (style/create-text-field
                     {:value (second a)
                      :onChange #(swap! state update-in [:attrs-list i]
                                  assoc 1 (-> % .-target .-value))
                      :disabled (not (:editing? @state))
                      :style (if-not (:editing? @state)
                               {:backgroundColor (:background-gray style/colors)}
                               {:backgroundColor "#fff"})})
                   (icons/font-icon
                     {:style {:paddingLeft "0.5em" :padding "1em 0.7em"
                              :color "red" :cursor "pointer"
                              :display (when-not (:editing? @state) "none")}
                      :onClick (fn [e]
                                 (if (contains? (:reserved-keys @state) i)
                                   (do
                                     (swap! state assoc :deleting? true)
                                     (delete-attribute props (first a)
                                       (fn [e] (swap! state #(-> %
                                                       (assoc :deleting? false)
                                                       (update-in [:reserved-keys] utils/delete i)
                                                       (update-in [:attrs-list] utils/delete i))))))
                                   (swap! state update-in [:attrs-list] utils/delete i)))}
                     :x)]
                  (common/clear-both)])
               (:attrs-list @state))
             ;TODO: new textfields should gain typing focus when adding new rows
             [:div {:style {:display (when-not (:editing? @state) "none")}}
              [comps/Button {:style :add :text "Add new"
                             :onClick #(swap! state update-in [:attrs-list] conj ["" ""])}]]])
          [:div {:style {:display
                         (when (or (or (:editing? @state)
                                     (not-empty (:attrs-list @state))) (:saving? @state)) "none")}}
           (style/create-paragraph [:em {} "There are no attributes to display"])]]]
        (common/clear-both)]))
   :render-sidebar
   (fn [{:keys [props state]}]
     [:div {:style {:float "left" :width 290 :marginRight 40}}
      (if-not (:editing? @state)
        [:div {}
         [comps/SidebarButton
          {:style :light :margin :bottom :color :button-blue
           :text "View summary" :icon :document
           :onClick (:on-done props)}]
         (when (:writer? props)
           [comps/SidebarButton
            {:style :light :color :button-blue
             :text "Edit attributes" :icon :pencil
             :onClick #(swap! state assoc
                        :editing? true
                        :reserved-keys (vec (range 0 (count (:attrs-list @state)))))}])]
        [:div {:style {:fontSize "106%" :lineHeight 1 :textAlign "center"}}
         [comps/SidebarButton
          {:color :success-green
           :text "Done" :icon :status-done
           :onClick (fn [e]
                      (swap! state assoc
                        :attrs-list (filterv
                                      (fn [pair] (not (clojure.string/blank? (clojure.string/trim (first pair)))))
                                      (:attrs-list @state))
                        :saving? true)
                      ;; TODO: rawls will soon return attributes after update- use that intead of reload
                      (add-update-attributes
                        props (:attrs-list @state)
                        (fn [e] (reload-attributes props state
                                  #(swap! state assoc :editing? false :saving? false)))))}]])])})

(react/defc WorkspaceCloner
  {:render
   (fn [{:keys [props refs state this]}]
     [comps/Dialog {:width 400 :dismiss-self (:dismiss props)
                    :content
                    (react/create-element
                      [comps/OKCancelForm
                       {:header "Clone Workspace to:"
                        :dismiss-self (:dismiss props)
                        :content
                        (react/create-element
                        [:div {}
                         (when (:working? @state)
                           [comps/Blocker {:banner "Cloning..."}])
                         (style/create-form-label "Namespace:")
                         (style/create-text-field {:ref "namespace"
                                                   :style {:width "100%"}
                                                   :defaultValue (get-in props [:workspace-id :namespace])
                                                   :placeholder "Required"
                                                   :onChange #(swap! state dissoc :error)})
                         (style/create-form-label "Name:")
                         (style/create-text-field {:ref "name"
                                                   :style {:width "100%"}
                                                   :defaultValue (get-in props [:workspace-id :name])
                                                   :placeholder "Required"
                                                   :onChange #(swap! state dissoc :error)})
                         (when (:error @state)
                           [:div {:style {:color (:exception-red style/colors)}}
                            (:error @state)])])
                        :ok-button
                        (react/create-element
                          [comps/Button {:text "OK" :ref "okButton"
                                         :onClick #(react/call :do-clone this)}])}])
                    :get-first-element-dom-node #(.getDOMNode (@refs "namespace"))
                    :get-last-element-dom-node #(.getDOMNode (@refs "okButton"))}])
   :component-did-mount
   (fn []
     (common/scroll-to-top 100))
   :do-clone
   (fn [{:keys [props refs state]}]
     (let [[namespace name] (common/get-text refs "namespace" "name")]
       (if (some blank? [namespace name])
         (swap! state assoc :error "All fields required")
         (do
           (swap! state assoc :working? true)
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/clone-workspace (:workspace-id props))
              :payload {:namespace namespace :name name}
              :headers {"Content-Type" "application/json"}
              :on-done (fn [{:keys [success? status-text]}]
                         (swap! state dissoc :working?)
                         (if success?
                           ((:dismiss props))
                           (swap! state assoc :error status-text)))})))))})

(defn- view-summary [state props ws status owner? this on-view-attributes]
  [:div {:style {:margin "45px 25px"}}
   (when (:deleting? @state)
     [comps/Blocker {:banner "Deleting..."}])
   (when (:editing-acl? @state)
     [AclEditor {:workspace-id (:workspace-id props)
                 :dismiss-self #(swap! state dissoc :editing-acl?)
                 :update-owners #(swap! state update-in [:server-response :workspace] assoc "owners" %)}])
   (when (:cloning? @state)
     [WorkspaceCloner {:dismiss #(swap! state dissoc :cloning?)
                       :workspace-id (:workspace-id props)}])
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
    [comps/SidebarButton {:style :light :margin :top :color :button-blue
                          :text "View attributes" :icon :document
                          :onClick on-view-attributes}]
    [comps/SidebarButton {:style :light :margin :top :color :button-blue
                          :text "Clone..." :icon :plus
                          :onClick #(swap! state assoc :cloning? true)}]
    (when owner?
      [comps/SidebarButton {:style :light :margin :top :color :exception-red
                            :text "Delete" :icon :trash-can
                            :onClick #(when (js/confirm "Are you sure?")
                                       (swap! state assoc :deleting? true)
                                       (react/call :delete this))}])]
   [:div {:style {:marginLeft 330}}
    (style/create-section-header "Workspace Owner")
    (style/create-paragraph
      [:div {}
       (interpose ", " (map #(style/render-email %) (ws "owners")))
       (when owner?
         [:span {}
          " ("
          (style/create-link
            #(swap! state assoc :editing-acl? true)
            "Edit sharing...")
          ")"])])
    (style/create-section-header "Description")
    (style/create-paragraph [:em {} "Description info not available yet"])
    (style/create-section-header "Tags")
    (style/create-paragraph (render-tags ["Fake" "Tag" "Placeholders"]))
    (style/create-section-header "Research Purpose")
    (style/create-paragraph [:em {} "Research purpose not available yet"])
    (style/create-section-header "Billing Account")
    (style/create-paragraph [:em {} "Billing account not available yet"])]
   (common/clear-both)])

(react/defc Summary
  {:get-initial-state
   (fn []
     {:viewing-attributes? false})
   :render
   (fn [{:keys [state props this]}]
     (cond
       (nil? (:server-response @state))
       [comps/Spinner {:text "Loading workspace..."}]
       (get-in @state [:server-response :error-message])
       (style/create-server-error-message (get-in @state [:server-response :error-message]))
       :else
       (let [ws (get-in @state [:server-response :workspace])
             owner? (= "OWNER" (ws "accessLevel"))
             writer? (or (= "WRITER" (ws "accessLevel")) owner?)
             status (common/compute-status ws)]
         (if (:viewing-attributes? @state)
           (let [ws-response ((get-in @state [:server-response :workspace]) "workspace")]
             [AttributeViewer {:ws ws :writer? writer? :on-done #(swap! state dissoc :viewing-attributes?)
                               :attrs-list (mapv (fn [[k v]] [k v]) (ws-response "attributes"))
                               :workspace-id (:workspace-id props)}])
           (view-summary state props ws status owner? this #(swap! state assoc :viewing-attributes? true))))))
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
