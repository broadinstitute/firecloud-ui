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


(defn- modal-background [state]
  {:backgroundColor "rgba(82, 129, 197, 0.4)"
   :display (when-not (:overlay-shown? @state) "none")
   :overflowX "hidden" :overflowY "scroll"
   :position "fixed" :zIndex 9999
   :top 0 :right 0 :bottom 0 :left 0
   :textAlign "left"})

(def modal-content
  {:transform "translate(-50%, 0px)"
   :backgroundColor "#f4f4f4"
   :position "relative" :marginBottom 60
   :top 60 :left "50%" :width 500})

(def form-label
  {:marginBottom "0.16667em" :fontSize "88%"})

(def text
  {:backgroundColor "#fff"
   :border "1px solid #cacaca" :borderRadius 3
   :boxShadow "0px 1px 3px rgba(0,0,0,0.06)" :boxSizing "border-box"
   :fontSize "88%"
   :marginBottom "0.75em" :padding "0.5em" :width "100%"})

(def select
  {:backgroundColor "#fff" ;;TODO background image?
   ;;:backgroundPosition "right center" :backgroundRepeat "no-repeat"
   :borderColor "#cacaca" :borderRadius 2 :borderWidth 1 :borderStyle "solid"
   :color "#000"
   :marginBottom "0.75em" :padding "0.33em 0.5em"
   :width "100%" :fontSize "88%"})

(defn- render-overlay [state refs]
  [:div {:style (modal-background state)}
   [:div {:style modal-content}
    [:div {:style {:backgroundColor "#fff"
                   :borderBottom "1px solid #e3e3e3"
                   :padding "20px 48px 18px"
                   :fontSize "137%" :fontWeight 400 :lineHeight 1}}
     "Create New Workspace"]
    [:div {:style {:padding "22px 48px 40px" :boxSizing "inherit"}}
     [:div {:style form-label} "Name Your Workspace"]
     [:input {:type "text" :style text :ref "wsName"}]
     [:div {:style form-label} "Workspace Description"]
     [:textarea {:style text :rows 10 :cols 30 :ref "wsDesc"}]
     [:div {:style form-label} "Research Purpose"]
     [:select {:style select} [:option {} "Option 1"] [:option {} "Option 2"] [:option {} "Option 3"]]
     [:div {:style form-label} "Billing Contact"]
     [:select {:style select} [:option {} "Option 1"] [:option {} "Option 2"] [:option {} "Option 3"]]
     [:div {:style form-label} "Share With (optional)"]
     [:input {:type "text" :style text :ref "shareWith"}]
     [:em {:style {:fontSize "69%"}} "Separate multiple emails with commas"]
     (let [clear-overlay (fn []
                           (set! (.-value (.getDOMNode (@refs "wsName"))) "")
                           (set! (.-value (.getDOMNode (@refs "wsDesc"))) "")
                           (set! (.-value (.getDOMNode (@refs "shareWith"))) "")
                           (swap! state assoc :overlay-shown? false))]
       [:div {:style {:marginTop 40 :textAlign "center"}}
        [:a {:style {:marginRight 27 :paddingTop "0.52em"
                     :display "inline-block" :cursor "pointer"
                     :fontSize "106%" :fontWeight 500
                     :verticalAlign "top"
                     :color (:button-blue common/colors)} :onClick clear-overlay}
         "Cancel"]
        [common/Button {:text "Create Workspace"
                        :onClick
                        #(let [n (.-value (.getDOMNode (@refs "wsName")))]
                           (clear-overlay)
                           (when-not (or (nil? n) (empty? n))
                             (swap! state update-in [:workspaces] conj
                               {"name" n
                                "status" :not-started
                                "sample-count" (rand-int 1000)
                                "workflow-count" (rand-int 10)
                                "size-gb" (/ (rand-int 20) 2)})))}]])]]])


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
