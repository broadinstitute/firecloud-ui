(ns org.broadinstitute.firecloud-ui.page.workspaces-list
  (:require
    [clojure.string :refer [split join replace]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.page.workspace.details :refer [WorkspaceDetails]]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc CreateWorkspaceDialog
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-project (first (:billing-projects props))})
   :render
   (fn [{:keys [props state refs this]}]
     [dialog/Dialog
      {:width 500
       :dismiss-self (:dismiss props)
       :content
       (react/create-element
         [dialog/OKCancelForm
          {:header "Create New Workspace"
           :content
           (react/create-element
             [:div {:style {:marginBottom -20}}
              (when (:creating-wf @state)
                [comps/Blocker {:banner "Creating Workspace..."}])
              (style/create-form-label "Google Project")
              (style/create-select
               {:value (:selected-project @state)
                :onChange #(swap! state assoc :selected-project (-> % .-target .-value))}
                (:billing-projects props))
              (style/create-form-label "Name")
              [input/TextField {:ref "wsName" :style {:width "100%"}
                                :predicates [(input/nonempty "Workspace name")
                                             (input/alphanumeric_- "Workspace name")]}]
              (style/create-textfield-hint "Only letters, numbers, underscores, and dashes allowed")
              (style/create-form-label "Description (optional)")
              (style/create-text-area {:style {:width "100%"} :rows 5 :ref "wsDescription"})
              [comps/ErrorViewer {:error (:server-error @state)}]
              (style/create-validation-error-message (:validation-errors @state))])
           :dismiss-self (:dismiss props)
           :ok-button
           (react/create-element
             [comps/Button
              {:text "Create Workspace" :ref "createButton"
               :onClick #(react/call :create-workspace this)}])}])}])
   :create-workspace
   (fn [{:keys [props state refs]}]
     (swap! state dissoc :server-error :validation-errors)
     (if-let [fails (input/validate refs "wsName")]
       (swap! state assoc :validation-errors fails)
       (let [project (nth (:billing-projects props) (int (:selected-project @state)))
             name (input/get-text refs "wsName")
             desc (common/get-text refs "wsDescription")
             attributes (if (clojure.string/blank? desc) {} {:description desc})]
         (swap! state assoc :creating-wf true)
         (endpoints/call-ajax-orch
           {:endpoint (endpoints/create-workspace project name)
            :payload {:namespace project :name name :attributes attributes}
            :headers {"Content-Type" "application/json"}
            :on-done (fn [{:keys [success? get-parsed-response]}]
                       (swap! state dissoc :creating-wf)
                       (if success?
                         (do ((:dismiss props))
                             (nav/navigate (:nav-context props) (str project ":" name)))
                         (swap! state assoc :server-error (get-parsed-response))))}))))})


(def row-height-px 56)


(react/defc StatusCell
  {:render
   (fn [{:keys [props]}]
     (let [status (get-in props [:data :status])]
       [:a {:href (nav/create-href (:nav-context props) (get-in props [:data :href]))
            :style {:display "block" :backgroundColor (style/color-for-status status)
                    :margin "2px 0 0 2px" :height (- row-height-px 4)
                    :position "relative"}}
        [:span {:style {:display "block" :backgroundColor "rgba(0,0,0,0.2)"
                        :position "absolute" :top 0 :right 0 :bottom 0 :left 0}}]
        (style/center {}
          (case status
            "Complete" [icons/CompleteIcon]
            "Running" [icons/RunningIcon]
            "Exception" [icons/ExceptionIcon]))]))})


(react/defc WorkspaceCell
  {:render
   (fn [{:keys [props]}]
     [:a {:href (nav/create-href (:nav-context props) (get-in props [:data :href]))
          :style {:display "block"
                  :backgroundColor (style/color-for-status (get-in props [:data :status]))
                  :height (- row-height-px 4)
                  :color "white" :textDecoration "none"}}
      [:span {:style {:display "block" :padding "1em 0 0 1em" :fontWeight 600}}
       (get-in props [:data :name])]])})

(defn- get-workspace-name-string [ws]
  (str (get-in ws ["workspace" "namespace"]) "/" (get-in ws ["workspace" "name"])))

(defn- get-max-workspace-name-length [workspaces]
  (apply max (map count (map #(get-workspace-name-string %) workspaces))))

(defn- get-workspace-description [ws]
  (not-empty (get-in ws ["workspace" "attributes" "description"])))

(defn- get-max-workspace-description-length [workspaces]
  (apply max (map count (map #(get-workspace-description %) workspaces))))

(react/defc WorkspaceTable
  {:render
   (fn [{:keys [props state]}]
     (let [border-style (str "1px solid " (:line-gray style/colors))
           max-workspace-name-length (get-max-workspace-name-length (:workspaces props))
           max-description-length (get-max-workspace-description-length (:workspaces props))]
       [table/Table
        {:empty-message "No workspaces to display."
         :cell-padding-left nil
         :header-row-style {:fontWeight nil :fontSize "90%"
                            :color (:text-light style/colors) :backgroundColor nil}
         :header-style {:padding "0.5em 0 0.5em 14px"}
         :resizable-columns? true :reorderable-columns? false :resize-tab-color (:line-gray style/colors)
         :body-style {:fontSize nil :fontWeight nil
                      :borderLeft border-style :borderRight border-style
                      :borderBottom border-style :borderRadius 4}
         :row-style {:height row-height-px :borderTop border-style}
         :cell-content-style {:padding nil}
         :toolbar (fn [built-in]
                    [:div {}
                     [:div {:style {:float "left"}} built-in]
                     (when-not (empty? (:billing-projects props))
                       [:div {:style {:float "right" :marginTop -5}}
                        [comps/Button
                         {:text "Create New Workspace..." :style :add
                          :onClick #(swap! state assoc :show-create-workspace? true)}]])
                     (common/clear-both)
                     (when (:show-create-workspace? @state)
                       [CreateWorkspaceDialog {:dismiss #(swap! state dissoc :show-create-workspace?)
                                               :billing-projects (:billing-projects props)
                                               :nav-context (:nav-context props)}])])
         :filters [{:text "All" :pred (constantly true)}
                   {:text "Complete" :pred #(= "Complete" (:status %))}
                   {:text "Running" :pred #(= "Running" (:status %))}
                   {:text "Exception" :pred #(= "Exception" (:status %))}]
         :columns
         [{:sort-by :none :filter-by :none :starting-width row-height-px :resizable? false
           :header [:div {:style {:marginLeft -6}} "Status"]
           :content-renderer (fn [data] [StatusCell {:data data
                                                     :nav-context (:nav-context props)}])}
          {:as-text :name :sort-by :text
           :header "Workspace" :starting-width (min 500 (* max-workspace-name-length 10))
           :content-renderer (fn [data] [WorkspaceCell {:data data
                                                        :nav-context (:nav-context props)}])}
          {:header "Description" :starting-width (min 500 (* max-description-length 10))
           :content-renderer (fn [description]
                               [:div {:style {:padding "0 0 16px 14px"}}
                                (if description description
                                  [:span {:style {:fontStyle "oblique"}}
                                   "No description provided"])])}
          {:header "Access Level" :starting-width 122 :resizable? false
           :sort-by #(case % "OWNER" 0 "WRITER" 1 "READER" 2) :sort-initial :asc
           :content-renderer
           (fn [accessLevel]
             [:div {:style {:padding "0 0 16px 14px"}}
              (clojure.string/capitalize accessLevel)])}]
         :data (:workspaces props)
         :->row (fn [ws]
                  (let [ws-name (get-workspace-name-string ws)
                        ws-href (let [x (ws "workspace")] (str (x "namespace") ":" (x "name")))]
                    [{:name ws-name :href ws-href :status (:status ws)}
                     {:name ws-name :href ws-href :status (:status ws)}
                     (get-workspace-description ws)
                     (get-in ws ["accessLevel"])]))}]))})


(react/defc WorkspaceList
  {:render
   (fn [{:keys [props state]}]
     (let [server-response (:server-response @state)
           {:keys [workspaces billing-projects error-message]} server-response]
       (cond
         error-message (style/create-server-error-message error-message)
         (some nil? [workspaces billing-projects]) [comps/Spinner {:text "Loading workspaces..."}]
         :else
         [:div {:style {:margin "0 2em"}}
          [WorkspaceTable
           (assoc props :workspaces workspaces :billing-projects billing-projects)]])))
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-workspaces
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (if success?
                     (swap! state update-in [:server-response]
                       assoc :workspaces (map
                                           (fn [ws]
                                             (assoc ws :status (common/compute-status ws)))
                                           (get-parsed-response)))
                     (swap! state update-in [:server-response]
                       assoc :error-message status-text)))})
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-billing-projects)
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (if success?
                     (swap! state update-in [:server-response]
                       assoc :billing-projects (get-parsed-response))
                     (swap! state update-in [:server-response]
                       assoc :error-message status-text)))}))})


(defn- create-breadcrumbs-from-hash [hash]
  (let [segments (split hash #"/")]
    (map-indexed
      (fn [index segment]
        (if (zero? index)
          {:text "Workspaces" :href "#workspaces"}
          {:text (replace (js/decodeURIComponent segment) ":" "/")
           :href (str "#" (join "/" (subvec segments 0 (inc index))))}))
      segments)))


(react/defc Page
  {:render
   (fn [{:keys [props refs]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           selected-ws-id (common/get-id-from-nav-segment (:segment nav-context))]
       [:div {}
        [:div {:style {:padding "2em"}}
         [:span {:style {:fontSize "180%"}}
          [comps/Breadcrumbs {:crumbs (create-breadcrumbs-from-hash (:hash nav-context))}]]]
        (if selected-ws-id
          [WorkspaceDetails {:workspace-id selected-ws-id
                             :nav-context nav-context
                             :on-delete #(nav/back nav-context)
                             :on-clone #(nav/navigate (:nav-context props) %)}]
          [WorkspaceList {:nav-context nav-context}])]))})
