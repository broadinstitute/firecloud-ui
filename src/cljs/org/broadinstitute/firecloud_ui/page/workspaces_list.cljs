(ns org.broadinstitute.firecloud-ui.page.workspaces-list
  (:require
    clojure.string
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
    [org.broadinstitute.firecloud-ui.page.workspace.details :refer [render-workspace-details]]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- render-modal [state refs nav-context]
  (react/create-element
    [dialog/OKCancelForm
     {:header "Create New Workspace"
      :content
      (react/create-element
        [:div {:style {:marginBottom -20}}
         (when (:creating-wf @state)
           [comps/Blocker {:banner "Creating Workspace..."}])
         (style/create-form-label "Google Project")
         [input/TextField {:ref "wsNamespace" :style {:width "100%"}
                           :defaultValue "broad-dsde-dev"
                           :predicates [(input/nonempty "Google Project")]}]
         (style/create-form-label "Name")
         [input/TextField {:ref "wsName" :style {:width "100%"}
                           :predicates [(input/nonempty "Workspace name")]}]
         (style/create-form-label "Description (optional)")
         (style/create-text-area {:style {:width "100%"} :rows 5 :ref "wsDescription"})
         [comps/ErrorViewer {:error (:server-error @state)}]
         (style/create-validation-error-message (:validation-error @state))])
      :dismiss-self #(swap! state dissoc :overlay-shown? :server-error :validation-error)
      :ok-button
      (react/create-element
        [comps/Button
         {:text "Create Workspace" :ref "createButton"
          :onClick #(if-let [fails (input/validate refs "wsNamespace" "wsName")]
                      (swap! state assoc :validation-error fails)
                      (let [desc (common/get-text refs "wsDescription")
                            [ns n] (input/get-text refs "wsNamespace" "wsName")
                            attributes (if (clojure.string/blank? desc) {} {:description desc})]
                        (swap! state assoc :creating-wf true :error nil :validation-error nil)
                        (endpoints/call-ajax-orch
                          {:endpoint (endpoints/create-workspace ns n)
                           :payload {:namespace ns :name n :attributes attributes}
                           :headers {"Content-Type" "application/json"}
                           :on-done (fn [{:keys [success? get-parsed-response]}]
                                      (swap! state dissoc :creating-wf)
                                      (if success?
                                        (do (swap! state dissoc :overlay-shown? :server-error :validation-error)
                                          (nav/navigate nav-context (str ns ":" n)))
                                        (swap! state assoc :server-error (get-parsed-response))))})))}])}]))


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
            "Complete" [icons/CompleteIcon]
            "Running" [icons/RunningIcon]
            "Exception" [icons/ExceptionIcon]))]))})


(react/defc WorkspaceCell
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:backgroundColor (style/color-for-status (get-in props [:data :status]))
                    :marginTop 2 :height "calc(100% - 4px)"
                    :color "white" :cursor "pointer"}
            :onClick #((get-in props [:data :onClick]))}
      [:div {:style {:padding "1em 0 0 1em" :fontWeight 600}}
       (get-in props [:data :name])]])})

(defn- get-workspace-name-string [ws]
  (str (get-in ws ["workspace" "namespace"]) "/" (get-in ws ["workspace" "name"])))

(defn- get-max-workspace-name-length [workspaces]
  (apply max (map count (map #(get-workspace-name-string %) workspaces))))

(defn- get-workspace-description [ws]
  (get-in ws ["workspace" "attributes" "description"]))

(defn- get-max-workspace-description-length [workspaces]
  (apply max (map count (cons (map #(get-workspace-description %) workspaces) ["No description provided"]))))

(react/defc WorkspaceTable
  {:render
   (fn [{:keys [state props]}]
     (let [border-style (str "1px solid " (:line-gray style/colors))
           max-workspace-name-length (get-max-workspace-name-length (:workspaces props))
           max-description-length (get-max-workspace-description-length (:workspaces props))]
       (utils/cljslog (str "max-workspace-name-length: " max-workspace-name-length))
       (utils/cljslog (str "max-description-length: " max-description-length))
       [table/Table
        {:empty-message "No workspaces to display."
         :cell-padding-left nil
         :header-row-style {:fontWeight nil :fontSize "90%"
                            :color (:text-light style/colors) :backgroundColor nil}
         :header-style {:padding "0.5em 0 0.5em 14px" :overflow nil}
         :resizable-columns? false :reorderable-columns? false
         :body-style {:fontSize nil :fontWeight nil
                      :borderLeft border-style :borderRight border-style
                      :borderBottom border-style :borderRadius 4}
         :row-style {:height 56 :borderTop border-style}
         :even-row-style {:backgroundColor nil}
         :cell-content-style {:padding nil}
         :toolbar (fn [built-in]
                    [:div {}
                     [:div {:style {:float "left"}} built-in]
                     [:div {:style {:float "right" :marginTop -5}}
                      [comps/Button
                       {:text "Create New Workspace..." :style :add
                        :onClick (:show-create-workspace props)}]]
                     (common/clear-both)])
         :filters [{:text "All" :pred (constantly true)}
                   {:text "Complete" :pred #(= "Complete" (:status %))}
                   {:text "Running" :pred #(= "Running" (:status %))}
                   {:text "Exception" :pred #(= "Exception" (:status %))}]
         :columns
         [{:sort-by :none :filter-by :none
           :header [:div {:style {:marginLeft -6}} "Status"] :starting-width 60
           :content-renderer (fn [data] [StatusCell {:data data}])}
          {:as-text :name :sort-by :text
           :header "Workspace" :starting-width  (* max-workspace-name-length 10)
           :content-renderer (fn [data] [WorkspaceCell {:data data}])}
          {:header "Description" :starting-width (* max-description-length 10)
           :content-renderer (fn [description]
                               [:div {:style {:padding "1.1em 0 0 14px"
                                              :fontStyle (when-not description "oblique")}}
                                (or description "No description provided")])}
          {:header "Access Level"  :starting-width 150
           :sort-by #(case % "OWNER" 0 "WRITER" 1 "READER" 2) :sort-initial :asc
           :content-renderer
           (fn [accessLevel]
             [:div {:style {:padding "1.1em 0 0 14px"}}
              (clojure.string/capitalize accessLevel)])}]
         :data (:workspaces props)
         :->row (fn [ws]
                  [{:status (:status ws)
                    :onClick #((:onWorkspaceSelected props) (ws "workspace"))}
                   {:name (get-workspace-name-string ws)
                    :status (:status ws)
                    :onClick #((:onWorkspaceSelected props) (ws "workspace"))}
                   (get-in ws ["workspace" "attributes" "description"])
                   (get-in ws ["accessLevel"])])}]))})


(react/defc WorkspaceList
  {:render
   (fn [{:keys [props state]}]
     (let [server-response (:server-response @state)]
       (cond
         (nil? server-response) [comps/Spinner {:text "Loading workspaces..."}]
         (not (:success? server-response))
         (style/create-server-error-message (:error-message server-response))
         :else
         [:div {:style {:margin "0 2em"}}
          [WorkspaceTable (assoc props :workspaces (:workspaces server-response))]])))
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


(defn- render-workspaces-list [state nav-context]
  (react/create-element
    WorkspaceList
    {:onWorkspaceSelected
     (fn [workspace]
       (nav/navigate
         nav-context (str (workspace "namespace") ":" (workspace "name")))
       (common/scroll-to-top))
     :show-create-workspace #(swap! state assoc :overlay-shown? true)}))


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
          [dialog/Dialog
           {:width 500
            :dismiss-self #(swap! state dissoc :overlay-shown?)
            :content (render-modal state refs nav-context)
            :get-first-element-dom-node #(.getDOMNode (@refs "wsNamespace"))
            :get-last-element-dom-node #(.getDOMNode (@refs "createButton"))}])
        [:div {:style {:padding "2em"}}
         [:span {:style {:fontSize "180%"}}
          (if selected-ws-id
            (str "Workspace: " (:namespace selected-ws-id) "/" (:name selected-ws-id))
            "Workspaces")]]
        (if selected-ws-id
          ;; TODO: add 'back' function to nav
          (render-workspace-details selected-ws-id
            #(set! (-> js/window .-location .-hash) "workspaces") (:nav-context props))
          (render-workspaces-list state nav-context))]))})
