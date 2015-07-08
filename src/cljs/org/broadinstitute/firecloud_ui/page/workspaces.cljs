(ns org.broadinstitute.firecloud-ui.page.workspaces
  (:require
   [clojure.string]
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.table :as table]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.utils :as utils :refer [rlog jslog cljslog]]
   ))


(react/defc StatusCell
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:backgroundColor "#7aac20" :margin "2px 0 0 2px" :height "calc(100% - 4px)"
                    :position "relative"}}
      [:div {:style {:backgroundColor "rgba(0,0,0,0.2)"
                     :position "absolute" :top 0 :right 0 :bottom 0 :left 2}}]])})


(react/defc WorkspaceCell
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:backgroundColor "#7aac20" :marginTop 2 :height "calc(100% - 4px)"
                    :color "white"}}
      [:div {:style {:padding "1em 0 0 1em" :fontWeight 600}}
       (:data props)]])})


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


(react/defc WorkspaceList
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:padding "0 4em"}}
      (if (zero? (count (:workspaces props)))
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
                         [(workspace "status")
                          (workspace "name")
                          (workspace "sample-count")
                          (workspace "workflow-count")
                          (workspace "size-gb")
                          "Me"])
                       (:workspaces props))})])])})


(react/defc FilterButtons
  (let [Button 
        (react/create-class
         {:render
          (fn [{:keys [props]}]
            [:div {:style {:float "left"
                           :backgroundColor (if (:active? props)
                                              (:button-blue style/colors)
                                              (:background-gray style/colors))
                           :color (when (:active? props) "white")
                           :marginLeft "1em" :padding "1ex" :width "16ex"
                           :border (str "1px solid " (:line-gray style/colors))
                           :borderRadius "2em"
                           :cursor "pointer"}}
             (:text props)])})]
    {:render
     (fn []
       [:div {:style {:display "inline-block" :marginLeft "-1em"}}
        [Button {:text "All (0)" :active? true}]
        [Button {:text "Complete (0)"}]
        [Button {:text "Running (0)"}]
        [Button {:text "Exception (0)"}]
        [:div {:style {:clear "both"}}]])}))


(defn- modal-background [state]
  {:backgroundColor "rgba(82, 129, 197, 0.4)"
   :display (when-not (:overlay-shown? @state) "none")
   :overflowX "hidden" :overflowY "scroll"
   :position "fixed" :zIndex 9999
   :top 0 :right 0 :bottom 0 :left 0})

(def ^:private modal-content
  {:transform "translate(-50%, 0px)"
   :backgroundColor "#f4f4f4"
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
                     :borderBottom "1px solid #e3e3e3"
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
                                                 :createdBy n
                                                 :createdDate (.toISOString (js/Date.))})
                                :delay-ms (rand-int 2000)}})))}]]]]]))


(defn- create-mock-workspaces []
  (map
    (fn [i]
      (let [ns (rand-nth ["broad" "public" "nci"])]
        {:namespace ns
         :name (str "Workspace " (inc i))
         :createdBy ns
         :createdDate (.toISOString (js/Date.))}))
    (range (rand-int 100))))


(react/defc Page
  {:render
   (fn [{:keys [state refs]}]
     [:div {}
      (render-overlay state refs)
      [:div {:style {:padding "2em"}}
       [:div {:style {:float "right" :display (when-not (:workspaces-loaded? @state) "none")}}
        [comps/Button
         {:text "Create New Workspace" :style :add
          :onClick #(swap! state assoc :overlay-shown? true)}]]
       [:span {:style {:fontSize "180%"}} "Workspaces"]]
      (cond
        (:workspaces-loaded? @state)
        [:div {}
         [:div {:style {:backgroundColor (:background-gray style/colors)
                        :borderTop (str "1px solid " (:line-gray style/colors))
                        :borderBottom (str "1px solid " (:line-gray style/colors))
                        :padding "0 1.5em"}}
          [comps/TabBar {:items ["Mine" "Shared" "Read-Only"]}]]
         [:div {:style {:padding "2em 0" :textAlign "center"}}
          [FilterButtons]]
         [:div {} [WorkspaceList {:ref "workspace-list" :workspaces (:workspaces @state)}]]]
        (:error-message @state) [:div {:style {:color "red"}}
                                  "FireCloud service returned error: " (:error-message @state)]
        :else [comps/Spinner {:text "Loading workspaces..."}])])
   :component-did-mount
   (fn [{:keys [state]}]
     (utils/ajax-orch
       "/workspaces"
       {:on-done (fn [{:keys [success? xhr]}]
                   (if success?
                     (let [workspaces (utils/parse-json-string (.-responseText xhr))]
                       (swap! state assoc :workspaces-loaded? true :workspaces workspaces))
                     (swap! state assoc :error-message (.-statusText xhr))))
        :canned-response {:responseText (utils/->json-string (create-mock-workspaces))
                          :status 200 :delay-ms (rand-int 2000)}}))})
