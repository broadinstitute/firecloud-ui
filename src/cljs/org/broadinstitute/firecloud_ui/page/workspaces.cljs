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
     [:div {:style {:backgroundColor (:success-green style/colors)
                    :margin "2px 0 0 2px" :height "calc(100% - 4px)"
                    :position "relative" :cursor "pointer"}
            :onClick #((get-in props [:data :onClick]))}
      [:div {:style {:backgroundColor "rgba(0,0,0,0.2)"
                     :position "absolute" :top 0 :right 0 :bottom 0 :left 2}}]])})


(react/defc WorkspaceCell
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:backgroundColor (:success-green style/colors)
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
                         [{:workspace workspace :onClick #((:onWorkspaceSelected props) workspace)}
                          {:name (workspace "name") :onClick #((:onWorkspaceSelected props) workspace)}
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
                                                 :createdBy n
                                                 :createdDate (.toISOString (js/Date.))})
                                :delay-ms (rand-int 2000)}})))}]]]]]))


(defn- create-section-header [text]
  [:h2 {:style {:fontSize "125%" :fontWeight 500}} text])



(defn- create-paragraph [& children]
  [:div {:style {:margin "17px 0 0.33333em 0" :paddingBottom "1.5em"
                 :fontSize "90%" :lineHeight 1.5}}
   children])

(defn- render-tags [tags]
  (let [tagstyle {:marginRight 13 :borderRadius 2 :padding "5px 13px"
                  :backgroundColor (:tag-background style/colors)
                  :color (:tag-foreground style/colors)
                  :display "inline-block" :fontSize "94%"}]
    [:div {}
      (map (fn [tag] [:span {:style tagstyle} tag]) tags)]))



(def workspace-tabs-view-margins "45px 25px")

(defn- render-workspace-summary [workspace]
  [:div {:style {:margin workspace-tabs-view-margins}}
   [:div {:style {:position "relative" :float "left" :display "inline-block"
                  :top 0 :left 0 :width 290 :marginRight 40 :height "100%"}}
    [:div {:style {:borderRadius 5 :backgroundColor (:success-green style/colors) :color "#fff"
                   :fontSize "125%" :fontWeight 400 :padding 25 :textAlign "center"}}

     "Complete"]
    [:div {:style {:marginTop 27}}
     [:div {:style {:backgroundColor "transparent" :color (:button-blue style/colors)
                    :border (str "1px solid " (:line-gray style/colors))

                    :fontSize "106%" :lineHeight 1 :position "relative"
                    :padding "0.9em 0em"
                    :textAlign "center" :cursor "pointer"
                    :textDecoration "none"}}
      "Edit this page"]]]
   [:div {}
    (create-section-header "Workspace Owner")
    (create-paragraph
      [:strong {} (workspace "createdBy")]
      " ("
      [:a {:href "#" :style {:color (:button-blue style/colors) :textDecoration "none"}}
       "shared with -1 people"]
      ")")
    (create-section-header "Description")
    (create-paragraph [:em {} "Description info not available yet"])
    (create-section-header "Tags")
    (create-paragraph (render-tags ["Fake" "Tag" "Placeholders"]))
    (create-section-header "Research Purpose")
    (create-paragraph [:em {} "Research purpose not available yet"])
    (create-section-header "Billing Account")
    (create-paragraph [:em {} "Billing account not available yet"])]
   [:div {:style {:clear "both"}}]])





(react/defc WorkspaceMethodsConfigurationsList
            {:render
             (fn [{:keys [props]}]
               [:div {:style {:padding "0 4em"}}
                ;;count the 'method-confs' to decide what to put in the component
                (if (zero? (count (:method-conf-name props)))
                  [:div {:style {:textAlign "center" :backgroundColor (:background-gray style/colors)
                                 :padding "1em 0" :borderRadius 8}}
                   (str (:no-confs-to-display-message props) ) ]
                  ;;if the count is NOT zero, then put a table here! :)
                  [table/Table
                   (let [
                         ;;define the cell-style to be this map
                         cell-style {:flexBasis "8ex" :flexGrow 1 :whiteSpace "nowrap" :overflow "hidden"
                                     :borderLeft (str "1px solid " (:line-gray style/colors))}
                         ;; define the header-label to be this function
                         ;; what does this function do????  It seems to apply a default or given padding to a text?
                         header-label (fn [text & [padding]]
                                        [:span {:style {:paddingLeft (or padding "1em")}}
                                         [:span {:style {:fontSize "90%"}} text]])]
                     {:columns [{:label (header-label "Conf Name")
                                 :style (merge cell-style {:borderLeft "none"})}
                                {:label (header-label "Conf Root Entity Type")
                                 :style cell-style
                                 :header-style {:borderLeft "none"}}
                                {:label (header-label "Last Updated")
                                 :style (merge cell-style {:flexBasis "30ex"})
                                 :header-style {:borderLeft "none"}}]
                      :data (map (fn [m]
                                   [(m "method-conf-name")
                                    (m "method-conf-root-ent-type")
                                    (m "method-conf-last-updated")])
                                 (:method-confs props))})])])})



(defn- create-mock-methodconfs []
;;this maps a function to random integers
;;the function that gets mapped is an anonymous function defined here
;;the value passed to the anonymous function is a random integer

;;FROM https://broadinstitute.atlassian.net/browse/DSDEEPB-10 (verbatim)

;; The scope of this story is strictly the listing of method configurations.
;;* name of the method configuration
;;* root entity type
;;* last updated?

;;  Acceptance Criteria:
;;  * Display as much information as possible from the API
;;  * Draft a story reflecting work that needs to be completed on the API and Orchestration layers
;;  * Needs to be reviewed by [~birger] before it can be closed
  (map
    (fn [i]
      {:method-conf-name (rand-nth ["rand_name_1" "rand_name_2" "rand_name_3"])
       :method-conf-root-ent-type (str "method_conf_root_ent_type" (inc i))
       :method-conf-last-updated (str "method_conf_last_updated" (inc i))})
    (range (rand-int 100))))



(def method-conf-ajax-call
  ;; GET /{workspaceNamespace}/{workspaceName}/methodconfigs
  (fn [ work_space_name_space_f work_space_name_f state_atom ]

    (let [
          url (str "/" work_space_name_space_f "/"   work_space_name_f "/methodconfigs"  )
          ]
      (utils/ajax-orch
        url
        {:on-done (fn [{:keys [success? xhr]}]
                    (if success?
                      (let [methods (utils/parse-json-string (.-responseText xhr))]
                        (swap! state_atom assoc :methods-loaded? true :methods methods))
                      (swap! state_atom assoc :error-message (.-statusText xhr))))
         :canned-response {:responseText (utils/->json-string (create-mock-methodconfs))
                           :status 200
                           :delay-ms (rand-int 2000)}})
      )                                                     ;;let
    )
  )




(react/defc render-workspace-method-configurations-react-component
            {
             ;;Invoked once, only on the client (not on the server), immediately after the initial rendering occurs.
             :component-did-mount
             (fn [{:keys [state]}]
               ;;(set! )
               ;; referring to the methods-loaded? (of the state) do AJAX/something to modify it here ...
               (swap! state assoc :method-confs create-mock-methodconfs )
               (swap! state assoc :method-confs-loaded? true)
               (method-conf-ajax-call "ws_ns" "ws_n"   state    )
               )

             :render
             ;;render must be a function
             (fn [{:keys [state]}]

               [:div {:style {:padding "1em"}}
                ;;[:h2 {} "Configurations"]
                (create-section-header "Method Configurations")
                [:div {}
                 (cond
                   (:method-confs-loaded? @state)
                   [:div {}
                    ;;"put 'call' to WorkspaceMethodsConfigurationsList here cause the confs have been SUCCESSFULLY loaded"
                        [
                         WorkspaceMethodsConfigurationsList
                            {
                             :no-confs-to-display-message "There are no method configurations to display."
                             ;;:method-conf-name "this is a name"
                             ;;:method-confs (:method-confs @state)
                             }
                         ]



                                              ;;(str (:methods-loaded? @state) (:method-content @state)    )
                                              ;;
                                              ;;(:table {} [:tr {} "tr content"] )
                                              ;;[:table {} [:tr {} "tr stuff"] [:tr {} "another row"] ]
                                              ]
                   (:error-message @state) [:div {:style {:color "red"}}
                                            "FireCloud service returned error: " (:error-message @state)]
                   :else [comps/Spinner {:text "Loading configurations..."}])]]

               )
             }
            )





(defn- render-selected-workspace [workspace]
  [:div {}
   [comps/TabBar {:key "selected"
                  :items [{:text "Summary" :component (render-workspace-summary workspace)}
                          {:text "Data"}
                          {:text "Method Configurations" :component [render-workspace-method-configurations-react-component] }
                          {:text "Methods"}
                          {:text "Monitor"}
                          {:text "Files"}]}]])


(defn- create-mock-workspaces []
  (map
    (fn [i]
      (let [ns (rand-nth ["broad" "public" "nci"])]
        {:namespace ns
         :name (str "Workspace " (inc i))
         :createdBy ns
         :createdDate (.toISOString (js/Date.))}))
    (range (rand-int 100))))


(defn- render-workspaces-list [state]
  (let [content [:div {}
                 [:div {:style {:padding "2em 0" :textAlign "center"}}
                  [FilterButtons]]
                 [:div {} [WorkspaceList {:ref "workspace-list" :workspaces (:workspaces @state)
                                          :onWorkspaceSelected
                                          (fn [workspace]
                                            (swap! state assoc :selected-workspace workspace))}]]]]
    [:div {}
     [comps/TabBar {:key "list"
                    :items [{:text "Mine" :component content}
                            {:text "Shared" :component content}
                            {:text "Read-Only" :component content}]}]]))


(react/defc Page
  {:render
   (fn [{:keys [state refs]}]
     [:div {}
      (render-overlay state refs)
      [:div {:style {:padding "2em"}}
       [:div {:style {:float "right" :display (when (or (not (:workspaces-loaded? @state))
                                                        (:selected-workspace @state)) "none")}}
        [comps/Button
         {:text "Create New Workspace" :style :add
          :onClick #(swap! state assoc :overlay-shown? true)}]]
       [:span {:style {:fontSize "180%"}} (if-let [ws (:selected-workspace @state)]
                                            (ws "name")
                                            "Workspaces")]]
      (cond
        (:selected-workspace @state) (render-selected-workspace (:selected-workspace @state))
        (:workspaces-loaded? @state) (render-workspaces-list state)
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
                          :status 200 :delay-ms (rand-int 2000)}}))
   :component-will-receive-props
   (fn [{:keys [state]}]
     (swap! state dissoc :selected-workspace))})
