(ns org.broadinstitute.firecloud-ui.page.workspaces-list
  (:require
    clojure.string
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.page.workspace.details :refer [render-workspace-details]]
    ))


(defn- render-modal [state refs nav-context]
  (react/create-element
    [comps/OKCancelForm
     {:header "Create New Workspace"
      :content
      (react/create-element
        [:div {}
         (when (:creating-wf @state)
           [comps/Blocker {:banner "Creating Workspace..."}])
         (style/create-form-label "Workspace Namespace")
         (style/create-text-field {:style {:width "100%"} :ref "wsNamespace"
                                   :defaultValue "broad-dsde-dev"})
         (style/create-form-label "Workspace Name")
         (style/create-text-field {:style {:width "100%"} :ref "wsName"})
         (style/create-form-label "Workspace Description")
         (style/create-text-area {:style {:width "100%"} :rows 10 :ref "wsDesc"})
         (style/create-form-label "Research Purpose")
         (style/create-select {} ["Option 1" "Option 2" "Option 3"])
         (style/create-form-label "Billing Contact")
         (style/create-select {} ["Option 1" "Option 2" "Option 3"])
         (style/create-form-label "Share With (optional)")
         (style/create-text-field {:style {:width "100%"} :ref "shareWith"})
         (style/create-hint "Separate multiple emails with commas")])
      :dismiss-self #(swap! state dissoc :overlay-shown?)
      :ok-button
      (react/create-element
        [comps/Button
         {:text "Create Workspace" :ref "createButton"
          :onClick #(let [[ns n] (common/get-text refs "wsNamespace" "wsName")]
                     (when-not (or (empty? ns) (empty? n))
                       (swap! state assoc :creating-wf true)
                       (endpoints/call-ajax-orch
                         {:endpoint (endpoints/create-workspace ns n)
                          :payload {:namespace ns :name n :attributes {}}
                          :on-done (fn [{:keys [success?]}]
                                     (swap! state dissoc :creating-wf)
                                     (if success?
                                       (do (swap! state dissoc :overlay-shown?)
                                           (nav/navigate nav-context (str ns ":" n)))
                                       (js/alert "Workspace creation failed")))})))}])}]))


(react/defc StatusCell
  {:render
   (fn [{:keys [props]}]
     (let [status (get-in props [:data :status])]
       [:div {:style {:backgroundColor (style/color-for-status status)
                      :margin "2px 0 0 2px" :height "calc(100% - 4px)"
                      :position "relative" :cursor "pointer"}
              :onClick #((get-in props [:data :onClick]))}
        [:div {:style {:backgroundColor "rgba(0,0,0,0.2)"
                       :position "absolute" :top 0 :right 0 :bottom 0 :left 2}}]
        (style/center {}
          (case status
            "Complete"  [comps/CompleteIcon]
            "Running"   [comps/RunningIcon]
            "Exception" [comps/ExceptionIcon]))]))})


(react/defc WorkspaceCell
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:backgroundColor (style/color-for-status (get-in props [:data :status]))
                    :marginTop 2 :height "calc(100% - 4px)"
                    :color "white" :cursor "pointer"}
            :onClick #((get-in props [:data :onClick]))}
      [:div {:style {:padding "1em 0 0 1em" :fontWeight 600}}
       (get-in props [:data :name])]])})


(defn- filter-workspaces [f workspaces]
  (case f
    :all workspaces
    :complete (filter (fn [ws] (= "Complete" (:status ws))) workspaces)
    :running (filter (fn [ws] (= "Running" (:status ws))) workspaces)
    :exception (filter (fn [ws] (= "Exception" (:status ws))) workspaces)))


(defn- render-table [props workspaces key]
  (let [border-style (str "1px solid " (:line-gray style/colors))]
    [table/Table
     {:key key
      :empty-message "No workspaces to display."
      :cell-padding-left nil
      :header-row-style {:fontWeight nil :fontSize "90%"
                         :color (:text-light style/colors) :backgroundColor nil}
      :header-style {:padding "0 0 1em 14px" :overflow nil}
      :resizable-columns? false :reorderable-columns? false :sortable-columns? false
      :body-style {:fontSize nil :fontWeight nil
                   :borderLeft border-style :borderRight border-style
                   :borderBottom border-style :borderRadius 4}
      :row-style {:height 56 :borderTop border-style}
      :even-row-style {:backgroundColor nil}
      :cell-content-style {:padding nil}
      :columns
      [{:header [:div {:style {:marginLeft -6}} "Status"] :starting-width 60
        :content-renderer (fn [data] [StatusCell {:data data}])
        :filter-by :none}
       {:header "Workspace" :starting-width 250
        :content-renderer (fn [data] [WorkspaceCell {:data data}])
        :filter-by :name}
       {:header "Description" :starting-width 400
        :content-renderer (fn [data]
                            [:div {:style {:padding "1.1em 0 0 14px"}}
                             "No data available."])
        :filter-by :none}]
      :data (map (fn [ws]
                   [{:status (:status ws)
                     :onClick #((:onWorkspaceSelected props) (ws "workspace"))}
                    {:name (get-in ws ["workspace" "name"]) :status (:status ws)
                     :onClick #((:onWorkspaceSelected props) (ws "workspace"))}
                    ws])
              workspaces)}]))


(react/defc WorkspaceList
  {:get-initial-state
   (fn []
     {:active-filter :all})
   :render
   (fn [{:keys [props state]}]
     (let [server-response (:server-response @state)]
       (cond
         (nil? server-response) [comps/Spinner {:text "Loading workspaces..."}]
         (not (:success? server-response))
         (style/create-server-error-message (:error-message server-response))
         :else
         (let [workspaces (:workspaces server-response)
               filtered-workspaces (filter-workspaces (:active-filter @state) workspaces)
               build-button (fn [name filter]
                              {:text (str name " ("
                                       (count (filter-workspaces filter workspaces))
                                       ")")
                               :active? (= filter (:active-filter @state))
                               :onClick #(swap! state assoc :active-filter filter)})]
           [:div {}
            [:div {:style {:marginBottom "2em" :textAlign "center"}}
             [comps/FilterButtons {:buttons [(build-button "All" :all)
                                             (build-button "Complete" :complete)
                                             (build-button "Running" :running)
                                             (build-button "Exception" :exception)]}]]
            [:div {:style {:margin "0 2em"}}
             (render-table props filtered-workspaces (:active-filter @state))]]))))
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-workspaces
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (if success?
                     (swap! state assoc :server-response
                       {:success? true :workspaces
                        (map
                          (fn [ws]
                            (assoc ws :status (common/compute-status ws)))
                          (get-parsed-response))})
                     (swap! state assoc :server-response
                       {:success? false :error-message status-text})))}))})


(defn- render-workspaces-list [nav-context]
  (react/create-element
    WorkspaceList
    {:onWorkspaceSelected
     (fn [workspace]
       (nav/navigate
         nav-context (str (workspace "namespace") ":" (workspace "name")))
       (common/scroll-to-top))}))


(defn- get-workspace-id-from-nav-segment [segment]
  (when-not (clojure.string/blank? segment)
    (let [[ns n] (clojure.string/split segment #":")]
      {:namespace ns :name n})))


(react/defc Page
  {:render
   (fn [{:keys [props state refs]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           selected-ws-id (get-workspace-id-from-nav-segment (:segment nav-context))]
       [:div {}
        (when (:overlay-shown? @state)
          [comps/Dialog
           {:width 500
            :dismiss-self #(swap! state dissoc :overlay-shown?)
            :content (render-modal state refs nav-context)
            :get-first-element-dom-node #(.getDOMNode (@refs "wsNamespace"))
            :get-last-element-dom-node #(.getDOMNode (@refs "createButton"))}])
        [:div {:style {:padding "2em"}}
         [:div {:style {:float "right" :display (when (:name selected-ws-id) "none")}}
          [comps/Button
           {:text "Create New Workspace" :style :add
            :onClick #(swap! state assoc :overlay-shown? true)}]]
         [:span {:style {:fontSize "180%"}}
          (if selected-ws-id (:name selected-ws-id) "Workspaces")]]
        (if selected-ws-id
          ;; TODO: add 'back' function to nav
          (render-workspace-details selected-ws-id #(set! (-> js/window .-location .-hash) "workspaces"))
          (render-workspaces-list nav-context))]))})
