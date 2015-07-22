(ns org.broadinstitute.firecloud-ui.page.workspaces-list
  (:require
   [clojure.string]
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.icons :as icons]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.common.table :as table]
   [org.broadinstitute.firecloud-ui.nav :as nav]
   [org.broadinstitute.firecloud-ui.page.workspace.workspace-summary :refer [render-workspace-summary]]
   [org.broadinstitute.firecloud-ui.page.workspace.workspace-data :refer [render-workspace-data]]
   [org.broadinstitute.firecloud-ui.page.workspace.workspace-method-confs :refer [render-workspace-method-confs]]
   [org.broadinstitute.firecloud-ui.utils :as utils :refer [parse-json-string]]
   ))


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
        (case status
          "Complete"  [comps/CompleteIcon]
          "Running"   [comps/RunningIcon]
          "Exception" [comps/ExceptionIcon])]))})


(react/defc WorkspaceCell
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:backgroundColor (style/color-for-status (get-in props [:data :status]))
                    :marginTop 2 :height "calc(100% - 4px)"
                    :color "white" :cursor "pointer"}
            :onClick #((get-in props [:data :onClick]))}
      [:div {:style {:padding "1em 0 0 1em" :fontWeight 600}}
       (get-in props [:data :name])]])})


(react/defc SamplesCell
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :margin 2 :padding "1em 0 0 1em" :fontWeight "bold"}}
      (:data props) [:span {:style {:color (:text-gray style/colors)}} " samples"]])})


(react/defc WorkflowsCell
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :margin 2 :padding "1em 0 0 1em" :fontWeight "bold"}}
      (:data props) [:span {:style {:color (:text-gray style/colors)}} " workflows"]])})


(react/defc DataSizeCell
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :margin 2 :padding "1em 0 0 1em" :fontWeight "bold"}}
      (:data props) [:span {:style {:color (:text-gray style/colors)}} " GB"]])})


(react/defc OwnerCell
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :margin 2 :padding "1em 0 0 1em" :fontWeight "bold"}}
      (:data props)])})


(defn- filter-workspaces [f workspaces]
  (case f
    :all workspaces
    :complete (filter (fn [ws] (= "Complete" (ws "status"))) workspaces)
    :running (filter (fn [ws] (= "Running" (ws "status"))) workspaces)
    :exception (filter (fn [ws] (= "Exception" (ws "status"))) workspaces)))

(react/defc WorkspaceList
  {:render
   (fn [{:keys [props]}]
    (let [filtered-workspaces (filter-workspaces (:filter props) (:workspaces props))]
     [:div {:style {:padding "0 4em"}}
      (if (zero? (count filtered-workspaces))
        [:div {:style {:textAlign "center" :backgroundColor (:background-gray style/colors)
                       :padding "1em 0" :borderRadius 8}}
         "No workspaces to display."]
        [table/Table
         (let [cell-style {:flexBasis "10ex" :flexGrow 1 :whiteSpace "nowrap" :overflow "hidden"
                           :borderLeft (str "1px solid " (:line-gray style/colors))}
               header-label (fn [text & [padding]]
                              [:span {:style {:paddingLeft (or padding "1em")}}
                               [:span {:style {:fontSize "90%"}} text]])]
           {:columns [{:label (header-label "Status" "1ex") :component StatusCell
                       :style {:flexBasis "7ex" :flexGrow 0}}
                      {:label (header-label "Workspace")
                       :style (merge cell-style {:flexBasis "15ex" :marginRight 2
                                                 :borderLeft "none"})
                       :component WorkspaceCell}
                      {:label (header-label "Description")
                       :style cell-style
                       :header-style {:borderLeft 0}
                       :component SamplesCell}
                      {:label ""
                       :style cell-style
                       :header-style {:borderLeft 0}
                       :component WorkflowsCell}
                      {:label ""
                       :style cell-style
                       :header-style {:borderLeft 0}
                       :component DataSizeCell}
                      {:label ""
                       :style cell-style
                       :header-style {:borderLeft 0}
                       :component OwnerCell}]
            :data (map (fn [workspace]
                         [{:status (workspace "status") :onClick #((:onWorkspaceSelected props) workspace)}
                          {:name (workspace "name") :status (workspace "status") :onClick #((:onWorkspaceSelected props) workspace)}
                          (workspace "sample-count")
                          (workspace "workflow-count")
                          (workspace "size-gb")
                          "Me"])
                    filtered-workspaces)})])]))})


(defn- modal-background [state]
  {:backgroundColor "rgba(82, 129, 197, 0.4)"
   :display (when-not (:overlay-shown? @state) "none")
   :overflowX "hidden" :overflowY "scroll"
   :position "fixed" :zIndex 9999
   :top 0 :right 0 :bottom 0 :left 0})

(def ^:private modal-content
  {:transform "translate(-50%, 0px)"
   :backgroundColor (:background-gray style/colors)
   :position "relative" :marginBottom 60
   :top 60 :left "50%" :width 500})

(defn- render-overlay [state refs]
  (let [clear-overlay (fn []
                        (common/clear! refs "wsName" "wsDesc" "shareWith")
                        (swap! state assoc :overlay-shown? false))]
    [:div {:style (modal-background state)
           :onKeyDown (common/create-key-handler [:esc] clear-overlay)}
     [:div {:style modal-content}
      [:div {:style {:backgroundColor "#fff"
                     :borderBottom (str "1px solid " (:line-gray style/colors))
                     :padding "20px 48px 18px"
                     :fontSize "137%" :fontWeight 400 :lineHeight 1}}
       "Create New Workspace"]
      [:div {:style {:padding "22px 48px 40px" :boxSizing "inherit"}}
       (style/create-form-label "Name Your Workspace")
       (style/create-text-field {:style {:width "100%"} :ref "wsName"})
       (style/create-form-label "Workspace Description")
       (style/create-text-area  {:style {:width "100%"} :rows 10 :ref "wsDesc"})
       (style/create-form-label "Research Purpose")
       (style/create-select {}  "Option 1" "Option 2" "Option 3")
       (style/create-form-label "Billing Contact")
       (style/create-select {}  "Option 1" "Option 2" "Option 3")
       (style/create-form-label "Share With (optional)")
       (style/create-text-field {:style {:width "100%"} :ref "shareWith"})
       (style/create-hint "Separate multiple emails with commas")
       [:div {:style {:marginTop 40 :textAlign "center"}}
        [:a {:style {:marginRight 27 :marginTop 2 :padding "0.5em"
                     :display "inline-block"
                     :fontSize "106%" :fontWeight 500 :textDecoration "none"
                     :color (:button-blue style/colors)}
             :href "javascript:;"
             :onClick clear-overlay
             :onKeyDown (common/create-key-handler [:space :enter] clear-overlay)}
         "Cancel"]
        [comps/Button {:text "Create Workspace"
                       :onClick
                       #(let [n (.-value (.getDOMNode (@refs "wsName")))]
                          (clear-overlay)
                          (when-not (or (nil? n) (empty? n))
                            (utils/ajax-orch
                              "/workspaces"
                              {:method :post
                               :data (utils/->json-string {:name n})
                               :on-done (fn [{:keys [xhr]}]
                                          (swap! state update-in [:workspaces] conj
                                                 (utils/parse-json-string (.-responseText xhr))))
                               :canned-response
                               {:responseText (utils/->json-string
                                                {:namespace "test"
                                                 :name n
                                                 :status (rand-nth ["Complete" "Running" "Exception"])
                                                 :createdBy n
                                                 :createdDate (.toISOString (js/Date.))})
                                :delay-ms (rand-int 2000)}})))}]]]]]))


(defn- render-selected-workspace [workspace entities]
  [:div {}
   (if workspace
     [comps/TabBar {:key "selected"
                    :items [{:text "Summary" :component (render-workspace-summary workspace)}
                            {:text "Data" :component (render-workspace-data entities)}
                            {:text "Method Configurations" :component (render-workspace-method-confs workspace)}
                            {:text "Methods"}
                            {:text "Monitor"}
                            {:text "Files"}]}]
     [:div {:style {:textAlign "center" :color (:exception-red style/colors)}}
      "Workspace not found."])])


(react/defc FilterButtons
  (let [Button
        (react/create-class
          {:render
           (fn [{:keys [props]}]
             (let [state (:state props)
                   filter (:filter props)
                   active? (= filter (:active-filter @state))]
               [:div {:style {:float "left"
                              :backgroundColor (if active?
                                                 (:button-blue style/colors)
                                                 (:background-gray style/colors))
                              :color (when active? "white")
                              :marginLeft "1em" :padding "1ex" :width "16ex"
                              :border (str "1px solid " (:line-gray style/colors))
                              :borderRadius "2em"
                              :cursor "pointer"}
                      :onClick #(swap! state assoc :active-filter filter)}
                (:text props)]))})]
    {:render
     (fn [{:keys [props]}]
       (let [state (:state props)
             build-text (fn [name f]
                          (str name " (" (count (filter-workspaces f (:workspaces @state))) ")"))]
         [:div {:style {:display "inline-block" :marginLeft "-1em"}}
          [Button {:text (build-text "All" :all) :state state :filter :all}]
          [Button {:text (build-text "Complete" :complete) :state state :filter :complete}]
          [Button {:text (build-text "Running" :running) :state state :filter :running}]
          [Button {:text (build-text "Exception" :exception) :state state :filter :exception}]
          [:div {:style {:clear "both"}}]]))}))

(defn- render-workspaces-list [state nav-context]
  (let [content [:div {}
                 [:div {:style {:padding "2em 0" :textAlign "center"}}
                  [FilterButtons {:state state}]]
                 [WorkspaceList
                  {:ref "workspace-list"
                   :workspaces (:workspaces @state)
                   :filter (:active-filter @state)
                   :onWorkspaceSelected
                   (fn [workspace]
                     (nav/navigate
                      nav-context (str (workspace "namespace") ":" (workspace "name")))
                     (common/scroll-to-top))}]]]
    [:div {}
     [comps/TabBar {:key "list"
                    :items [{:text "Mine" :component content}
                            {:text "Shared" :component content}
                            {:text "Read-Only" :component content}]}]]))


(defn- create-mock-workspaces []
  (map
    (fn [i]
      (let [ns (rand-nth ["broad" "public" "nci"])
            status (rand-nth ["Complete" "Running" "Exception"])]
        {:namespace ns
         :name (str "Workspace " (inc i))
         :status status
         :createdBy ns
         :createdDate (.toISOString (js/Date.))}))
    (range (rand-int 100))))

(defn- create-mock-entities []
  (map
    (fn [i]
      {:entityType (rand-nth ["sample"])
       :name (str "entity" (inc i))
       :attributes (str "entity" (inc i) " has no attributes")})
    (range (rand-int 20))))


(defn- mock-live-data [workspaces]
  (map #(assoc % "status" "Complete") workspaces))


(defn- get-workspace-from-nav-segment [workspaces segment]
  (when (and workspaces (not (clojure.string/blank? segment)))
    (let [[ns n] (clojure.string/split segment #":")]
      [ns n (first (filter
                    (fn [ws] (and (= (ws "namespace") ns) (= (ws "name") n)))
                    workspaces))])))


(react/defc Page
  {:get-initial-state
   (fn [] {:active-filter :all})
   :render
   (fn [{:keys [props state refs]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           [selected-ws-ns selected-ws-name selected-ws]
           (get-workspace-from-nav-segment (:workspaces @state) (:segment nav-context))]
       [:div {}
        (render-overlay state refs)
        [:div {:style {:padding "2em"}}
         [:div {:style {:float "right" :display (when (or (not (:workspaces-loaded? @state))
                                                          selected-ws-name) "none")}}
          [comps/Button
           {:text "Create New Workspace" :style :add
            :onClick #(swap! state assoc :overlay-shown? true)}]]
         [:span {:style {:fontSize "180%"}} (if selected-ws-name selected-ws-name "Workspaces")]]
        (cond
          selected-ws-name (render-selected-workspace selected-ws (:entities @state))
          (:workspaces-loaded? @state) (render-workspaces-list state nav-context)
          (:error @state) (style/create-server-error-message (get-in @state [:error :message]))
          :else [comps/Spinner {:text "Loading workspaces..."}])]))
   :component-did-mount
   (fn [{:keys [state]}]
     (utils/ajax-orch
       "/workspaces"
       {:on-done (fn [{:keys [success? xhr]}]
                   (if success?
                     (let [workspaces (utils/parse-json-string (.-responseText xhr))]
                       (swap! state assoc :workspaces-loaded? true
                                          :workspaces (if utils/use-live-data?
                                                        (mock-live-data workspaces)
                                                        workspaces)))
                     (swap! state assoc
                            :error {:message (.-statusText xhr)})))
        :canned-response {:responseText (utils/->json-string (create-mock-workspaces))
                          :status 200 :delay-ms (rand-int 2000)}})
     (utils/ajax-orch
       "/workspaces/broad-dsde-dev/testws/entities/sample"
       {:on-done (fn [{:keys [success? xhr]}]
                   (if success?
                     (let [entities (utils/parse-json-string (.-responseText xhr))]
                       (swap! state assoc :entities-loaded? true
                              :entities entities))
                     (swap! state assoc :error-message (.-statusText xhr))))
        :canned-response {:responseText (utils/->json-string (create-mock-entities))
                          :status 200
                          :delay-ms (rand-int 2000)}}))})
