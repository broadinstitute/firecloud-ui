(ns org.broadinstitute.firecloud-ui.page.workspaces
  (:require
   [clojure.string]
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.table :as table]
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
      (:data props) [:span {:style {:color (:text-gray common/colors)}} " samples"]])})


(react/defc WorkflowsCell
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :margin 2 :padding "1em 0 0 1em" :fontWeight "bold"}}
      (:data props) [:span {:style {:color (:text-gray common/colors)}} " workflows"]])})


(react/defc DataSizeCell
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :margin 2 :padding "1em 0 0 1em" :fontWeight "bold"}}
      (:data props) [:span {:style {:color (:text-gray common/colors)}} " GB"]])})


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
        [:div {:style {:textAlign "center" :backgroundColor (:background-gray common/colors)
                       :padding "1em 0" :borderRadius 8}}
         "No workspaces to display."]
        [table/Table
         (let [cell-style {:flexBasis "10ex" :flexGrow 1 :whiteSpace "nowrap" :overflow "hidden"
                           :borderLeft (str "1px solid " (:line-gray common/colors))}
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
                                              (:button-blue common/colors)
                                              (:background-gray common/colors))
                           :color (when (:active? props) "white")
                           :marginLeft "1em" :padding "1ex" :width "16ex"
                           :border (str "1px solid " (:line-gray common/colors))
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


(defn- render-overlay [state refs]
  [:div {:style {:display (if-not (:overlay-shown? @state) "none")
                 :position "absolute"
                 :top 0 :left 0 :width "100%" :height "100%"
                 :background "rgba(82, 129, 197, 0.4)" :zIndex "99999"
                 :textAlign "center"}}
   [:div {:style {:width 300 :margin "100px auto"
                  :backgroundColor "white" :border "1px solid black"
                  :padding 15
                  :textAlign "center"}}
    [:div {} "Enter a name for the new Workspace:"]
    [:div {:style {:padding "10px"}} [:input {:ref "wsName" :type "text"}]]
    (let [clear-overlay (fn []
                          (set! (.-value (.getDOMNode (@refs "wsName"))) "")
                          (swap! state assoc :overlay-shown? false))]
      [:div {}
       [:button {:onClick #(let [n (.-value (.getDOMNode (@refs "wsName")))]
                             (clear-overlay)
                             (when-not (or (nil? n) (empty? n))
                               (swap! state update-in [:workspaces] conj
                                 {"name" n
                                  "status" :not-started
                                  "sample-count" (rand-int 1000)
                                  "workflow-count" (rand-int 10)
                                  "size-gb" (/ (rand-int 20) 2)})))}
        "OK"]
       [:button {:onClick clear-overlay} "Cancel"]])]])


(react/defc Page
  {:render
   (fn [{:keys [state refs]}]
     [:div {}
      (render-overlay state refs)
      [:div {:style {:padding "2em"}}
       [:div {:style {:float "right" :display (when-not (:workspaces-loaded? @state) "none")}}
        [common/Button
         {:text "Create New Workspace" :style :add
          :onClick #(swap! state assoc :overlay-shown? true)}]]
       [:span {:style {:fontSize "180%"}} "Workspaces"]]
      (if-not (:workspaces-loaded? @state)
        [common/Spinner {:text "Loading workspaces..."}]
        [:div {}
         [:div {:style {:backgroundColor (:background-gray common/colors)
                        :borderTop (str "1px solid " (:line-gray common/colors))
                        :borderBottom (str "1px solid " (:line-gray common/colors))
                        :padding "0 1.5em"}}
          [common/TabBar {:items ["Mine" "Shared" "Read-Only"]}]]
         [:div {:style {:padding "2em 0" :textAlign "center"}}
          [FilterButtons]]
         [:div {} [WorkspaceList {:ref "workspace-list" :workspaces (:workspaces @state)}]]])])
   :component-did-mount
   (fn [{:keys [state]}]
     (utils/ajax {:url "todo"
                  :on-done (fn [{:keys [xhr]}]
                             (let [workspaces (js->clj (js/JSON.parse (.-responseText xhr)))]
                               (swap! state assoc :workspaces-loaded? true :workspaces workspaces)))
                  :canned-response
                  {:responseText (js/JSON.stringify
                                  (clj->js
                                   (if (zero? (rand-int 2))
                                     []
                                     [{"name" "My First Workspace"
                                       "status" :not-started
                                       "sample-count" (rand-int 1000)
                                       "workflow-count" (rand-int 10)
                                       "size-gb" (/ (rand-int 20) 2)}])))
                   :delay-ms 1000}}))})
