(ns org.broadinstitute.firecloud-ui.page.workspaces-list
  (:require
    clojure.string
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.page.workspace.details :refer [render-workspace-details]]
    [org.broadinstitute.firecloud-ui.paths :as paths]
    [org.broadinstitute.firecloud-ui.utils :as utils :refer [parse-json-string]]
    ))


(defn- clear-overlay [state refs]
  (common/clear! refs "wsName" "wsDesc" "shareWith")
  (swap! state assoc :overlay-shown? false))


(defn- render-modal [state refs nav-context]
  (react/create-element
    [:div {}
     [:div {:style {:backgroundColor "#fff"
                    :borderBottom (str "1px solid " (:line-gray style/colors))
                    :padding "20px 48px 18px"
                    :fontSize "137%" :fontWeight 400 :lineHeight 1}}
      "Create New Workspace"]
     [:div {:style {:padding "22px 48px 40px" :backgroundColor (:background-gray style/colors)}}
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
      (style/create-hint "Separate multiple emails with commas")
      [:div {:style {:marginTop 40 :textAlign "center"}}
       [:a {:style {:marginRight 27 :marginTop 2 :padding "0.5em"
                    :display "inline-block"
                    :fontSize "106%" :fontWeight 500 :textDecoration "none"
                    :color (:button-blue style/colors)}
            :href "javascript:;"
            :onClick #(clear-overlay state refs)
            :onKeyDown (common/create-key-handler [:space :enter] #(clear-overlay state refs))}
        "Cancel"]
       (when (:creating-wf @state)
         [comps/Blocker {:banner "Creating Workflow..."}])
       [comps/Button {:text "Create Workspace" :ref "createButton"
                      :onClick
                      #(let [ns (-> (@refs "wsNamespace") .getDOMNode .-value clojure.string/trim)
                             n (-> (@refs "wsName") .getDOMNode .-value clojure.string/trim)]
                        (when-not (or (empty? ns) (empty? n))
                          (swap! state assoc :creating-wf true)
                          (utils/ajax-orch
                            (paths/create-workspace-path)
                            {:method :post
                             :data (utils/->json-string {:namespace ns :name n :attributes {}})
                             :on-done (fn [{:keys [success?]}]
                                        (swap! state dissoc :creating-wf)
                                        (if success?
                                          (do (clear-overlay state refs)
                                              (nav/navigate nav-context (str ns ":" n)))
                                          (js/alert "Workspace creation failed.")))
                             :canned-response
                             {:status 200
                              :responseText (utils/->json-string
                                              {:namespace ns
                                               :name n
                                               :createdBy n
                                               :createdDate (.toISOString (js/Date.))
                                               :bucketName n
                                               :attributes {}})
                              :delay-ms (rand-int 2000)}})))}]]]]))


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
      :resizable-columns? false :reorderable-columns? false
      :body-style {:fontSize nil :fontWeight nil
                   :borderLeft border-style :borderRight border-style
                   :borderBottom border-style :borderRadius 4}
      :row-style {:height 56 :borderTop border-style}
      :even-row-style {:backgroundColor nil}
      :cell-content-style {:padding nil}
      :columns
      [{:header [:div {:style {:marginLeft -6}} "Status"] :starting-width 60
        :content-renderer (fn [row-index data]
                            [StatusCell {:data data}])
        :filter-by :none}
       {:header "Workspace" :starting-width 250
        :content-renderer (fn [row-index data]
                            [WorkspaceCell {:data data}])
        :filter-by :name}
       {:header "Description" :starting-width 400
        :content-renderer (fn [row-index data]
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


(defn- create-mock-workspaces []
  (map
    (fn [i]
      (let [ns (rand-nth ["broad" "public" "nci"])
            status (rand-nth ["Complete" "Running" "Exception"])]
        {:accessLevel "OWNER"
         :workspace {:namespace ns
                     :name (str "Workspace " (inc i))
                     :status status
                     :createdBy ns
                     :createdDate (.toISOString (js/Date.))}
         :workspaceSubmissionStats {:runningSubmissionsCount (rand-int 2)
                                    :lastSuccessDate (rand-nth [nil (utils/rand-recent-time)])
                                    :lastFailureDate (rand-nth [nil (utils/rand-recent-time)])}
         :owners ["test@broadinstitute.org"]}))
    (range (rand-int 100))))


(defn- compute-status [workspace]
  (let [count (get-in workspace ["workspaceSubmissionStats" "runningSubmissionsCount"])]
    (cond (not (nil? (get-in workspace ["workspaceSubmissionStats" "lastFailureDate"]))) "Exception"
          (zero? count) "Complete"
          :else "Running")))


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
     (utils/call-ajax-orch
       (paths/list-workspaces-path)
       {:on-success (fn [{:keys [parsed-response]}]
                      (swap! state assoc :server-response
                        {:success? true
                         :workspaces
                         (map
                           (fn [ws]
                             (assoc ws :status (compute-status ws)))
                           parsed-response)}))
        :on-failure (fn [{:keys [status-text]}]
                      (swap! state assoc :server-response
                        {:success? false :error-message status-text}))
        :mock-data (create-mock-workspaces)}))})


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
          (render-workspace-details selected-ws-id)
          (render-workspaces-list nav-context))]))})
