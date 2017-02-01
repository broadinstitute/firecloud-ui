(ns broadfcui.page.workspaces-list
  (:require
    [clojure.string :refer [split join split-lines]]
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.overlay :as overlay]
    [broadfcui.common.style :as style]
    [broadfcui.common.table :as table]
    [broadfcui.common.table-utils :as table-utils]
    [broadfcui.config :as config]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.page.workspace.create :as create]
    [broadfcui.page.workspace.details :refer [WorkspaceDetails]]
    [broadfcui.persistence :as persistence]
    [broadfcui.utils :as utils]
    ))


(def row-height-px 56)

(def disabled-text
  (str "This workspace contains controlled access data. You can only access the contents of this workspace if you are "
       "dbGaP authorized for TCGA data and have linked your FireCloud account to your eRA Commons account."))

(react/defc StatusCell
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [nav-context data]} props
           {:keys [href status disabled?]} data]
       [:a {:href (if disabled?
                    "javascript:;"
                    (nav/create-href nav-context href))
            :style {:display "block" :position "relative"
                    :backgroundColor (if disabled? (:disabled-state style/colors) (style/color-for-status status))
                    :margin "2px 0 2px 2px" :height (- row-height-px 4)
                    :cursor (when disabled? "default")}
            :title (when disabled? disabled-text)}
        [:span {:style {:position "absolute" :top 0 :right 0 :bottom 0 :left 0
                        :backgroundColor "rgba(0,0,0,0.2)"}}]
        (style/center {}
          (case status
            "Complete" [icons/CompleteIcon]
            "Running" [icons/RunningIcon]
            "Exception" [icons/ExceptionIcon]))]))})


(react/defc WorkspaceCell
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [nav-context data]} props
           {:keys [href status protected? disabled?]} data
           color (style/color-for-status status)]
       [:a {:href (if disabled?
                    "javascript:;"
                    (nav/create-href nav-context href))
            :style {:display "flex" :alignItems "center"
                    :backgroundColor (if disabled? (:disabled-state style/colors) color)
                    :color "white" :textDecoration "none"
                    :cursor (when disabled? "default")
                    :height (- row-height-px 4)
                    :margin "2px 0"}
            :title (when disabled? disabled-text)}
        (when protected?
          [:span {:style {:display "block" :position "relative"}}
           [:span {:style {:display "block" :position "absolute" :left -17 :top -9
                           :width (- row-height-px 4) :padding "4px 0"
                           :backgroundColor "white" :color "#666" :fontSize "xx-small"
                           :transform "rotate(-90deg)"}}
            "RESTRICTED"]])
        [:span {:style {:paddingLeft 24 :fontWeight 600}}
         (:name data)]]))})

(defn- get-workspace-name-string [ws]
  (str (get-in ws ["workspace" "namespace"]) "/" (get-in ws ["workspace" "name"])))

(defn- get-workspace-description [ws]
  (not-empty (get-in ws ["workspace" "attributes" "description"])))

(defn- get-max-length [func workspaces]
  (->> workspaces (map func) (map count) (apply max)))

(def ^:private access-types ["Project Owner" "Owner" "Writer" "Reader" "No Access"])
(def ^:private access-types-defaults [true true true true true])
(def ^:private access-predicates
  {"Project Owner" #(= "PROJECT_OWNER" (% "accessLevel"))
   "Owner" #(= "OWNER" (% "accessLevel"))
   "Writer" #(= "WRITER" (% "accessLevel"))
   "Reader" #(= "READER" (% "accessLevel"))
   "No Access" #(= "NO ACCESS" (% "accessLevel"))
   })

(def ^:private realm-types ["Un-published" "Published" "TCGA Open Access" "TCGA Protected Access"])
(def ^:private realm-types-defaults [true false false false])
(def ^:private realm-predicates
 {"Published" #(and (not= (config/tcga-namespace) (get-in % ["workspace" "namespace"])) (get-in % ["workspace" "attributes" "library:published"]))
  "Un-published" #(and (not= (config/tcga-namespace) (get-in % ["workspace" "namespace"])) (not (get-in % ["workspace" "attributes" "library:published"])))
  "TCGA Open Access" #(and (= (config/tcga-namespace) (get-in % ["workspace" "namespace"]))
                           (not (get-in % ["workspace" "realm"])))
  "TCGA Protected Access" #(and (= (config/tcga-namespace) (get-in % ["workspace" "namespace"]))
                                (get-in % ["workspace" "realm"]))
  })

(def ^:private persistence-key "workspace-table-types")
(def ^:private VERSION 1)


(react/defc WorkspaceTable
  {:get-initial-state
   (fn []
     (let [persisted (persistence/try-restore
                       {:key persistence-key
                        :initial (fn [] {:v VERSION
                                         :selected-types (merge (zipmap access-types access-types-defaults) (zipmap realm-types realm-types-defaults))})})]
       (if (persistence/is-valid (comp (partial = VERSION) :v) persisted)
         persisted
         {:v VERSION :selected-types (merge (select-keys (:selected-types persisted) access-types) (zipmap realm-types realm-types-defaults))})))
   :render
   (fn [{:keys [props state refs]}]
     (let [max-workspace-name-length (get-max-length get-workspace-name-string (:workspaces props))
           max-description-length (get-max-length get-workspace-description (:workspaces props))]
       [:div {}
        (when (:show-access-level-select? @state)
          (let [checkbox (fn [label]
                           (let [stateval (get (:selected-types @state) label)]
                             [:div {}
                              [:label {:style {:cursor "pointer"}}
                               [:input {:type "checkbox"
                                        :checked (not (false? stateval))
                                        :onChange #(swap! state update-in [:selected-types label] false?)
                                        :style {:cursor "pointer"}}]
                               [:span {:style {:marginLeft "0.5ex"}} label]]]))]
            [overlay/Overlay {:get-anchor-dom-node #(react/find-dom-node (@refs "anchor"))
                              :anchor-x :right :anchor-y :bottom
                              :dismiss-self #(swap! state dissoc :show-access-level-select?)
                              :content
                              (react/create-element
                               [:div {:style {:padding "1em" :border style/standard-line}}
                                (map checkbox access-types)
                                [:hr {:style {:size "1px" :noshade true}}]
                                (map checkbox realm-types)])}]))
        [table/Table
         {:state-key "workspace-table"
          :empty-message "No workspaces to display." :retain-header-on-empty? true
          :cell-padding-left nil
          :initial-rows-per-page 10
          :header-row-style {:fontWeight nil :fontSize "90%"
                             :color (:text-lighter style/colors) :backgroundColor nil}
          :header-style {:padding "0.5em 0 0.5em 14px"}
          :resizable-columns? true :reorderable-columns? false :resize-tab-color (:line-default style/colors)
          :body-style {:fontSize nil :fontWeight nil
                       :borderLeft style/standard-line :borderRight style/standard-line
                       :borderBottom style/standard-line}
          :row-style {:height row-height-px :borderTop style/standard-line}
          :cell-content-style {:padding nil}
          :toolbar (table-utils/add-right
                    [create/Button (select-keys props [:nav-context :billing-projects :disabled-reason])])
          :filter-groups [{:text "All" :pred (constantly true)}
                          {:text "Complete" :pred #(= "Complete" (:status %))}
                          {:text "Running" :pred #(= "Running" (:status %))}
                          {:text "Exception" :pred #(= "Exception" (:status %))}]
          :columns
          [{:sort-by :none :filter-by :none :starting-width row-height-px :resizable? false
            :header [:div {:style {:marginLeft -6}} "Status"] :header-key "Status"
            :as-text :status
            :content-renderer (fn [data] [StatusCell {:data data
                                                      :nav-context (:nav-context props)}])}
           {:as-text :name :sort-by :text
            :header [:span {:style {:marginLeft 10}} "Workspace"] :header-key "Workspace"
            :starting-width (min 500 (* max-workspace-name-length 10))
            :content-renderer (fn [data] [WorkspaceCell {:data data
                                                         :nav-context (:nav-context props)}])}
           {:header "Description" :starting-width (max 200 (min 500 (* max-description-length 10)))
            :content-renderer (fn [description]
                                [:div {:style {:paddingLeft 14}}
                                 (if description (-> description split-lines first)
                                                 [:span {:style {:fontStyle "italic"}}
                                                  "No description provided"])])}
           {:as-text common/format-date
            :header "Last Modified Date" :starting-width 300
            :content-renderer (fn [date]
                                [:div {:style {:paddingLeft 14}} (common/format-date date) ])}
           {:header "Access Level" :starting-width 118 :resizable? false
            :sort-by #(case % "OWNER" 0 "WRITER" 1 "READER" 2 "NO ACCESS" 3 4) :sort-initial :asc
            :content-renderer
            (fn [accessLevel]
              [:div {:style {:paddingLeft 14}}
               (if (= accessLevel "PROJECT_OWNER")
                 "Project Owner"
                 (clojure.string/capitalize accessLevel))])}
           {:header (react/create-element
                     [:span {:ref "anchor"
                             :style {:cursor "pointer" :padding "0.1em 0.3em" :borderRadius 2 :marginLeft -14
                                     :border style/standard-line}
                             :onClick #(swap! state assoc :show-access-level-select? true)}
                      "Include..."])
            :header-key "Include" :starting-width 68 :resizable? false :sort-by :none}]
          :data (let [somepred (fn[preds]
                                (->> (merge preds (:selected-types @state))
                                     (keep (fn [[k v]] (when (and (not (false? v)) (some? (preds k))) k)))
                                     (map preds)
                                     (cons (constantly false)) ;; keeps (apply some-fn) from bombing when the list is empty
                                     (apply some-fn)))]
                  (filter
                    (every-pred
                      (somepred access-predicates)
                      (somepred realm-predicates))
                    (:workspaces props)))
          :->row (fn [ws]
                   (let [ws-name (get-workspace-name-string ws)
                         ws-href (let [x (ws "workspace")] (str (x "namespace") ":" (x "name")))
                         disabled? (= (ws "accessLevel") "NO ACCESS")]
                     [{:name ws-name :href ws-href :status (:status ws) :disabled? disabled?}
                      {:name ws-name :href ws-href :status (:status ws) :disabled? disabled?
                       :protected? (get-in ws ["workspace" "realm"])}
                      (get-workspace-description ws)
                      (get-in ws ["workspace" "lastModified"])
                      (ws "accessLevel")
                      nil]))}]]))
   :component-did-update
   (fn [{:keys [state]}]
     (persistence/save {:key persistence-key :state state :only [:v :selected-types]}))})


(react/defc WorkspaceList
  {:get-initial-state
   (fn []
     {:server-response {:disabled-reason :not-loaded}})
   :render
   (fn [{:keys [props state]}]
     (let [server-response (:server-response @state)
           {:keys [workspaces billing-projects error-message disabled-reason]} server-response]
       (cond
         error-message (style/create-server-error-message error-message)
         (some nil? [workspaces billing-projects]) [comps/Spinner {:text "Loading workspaces..."}]
         :else
         [:div {:style {:margin "0 2em"}}
          [WorkspaceTable
           (assoc props
             :workspaces workspaces
             :billing-projects billing-projects
             :disabled-reason disabled-reason)]])))
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-workspaces
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (if success?
                     (swap! state update :server-response
                       assoc :workspaces (map
                                           (fn [ws]
                                             (assoc ws :status (common/compute-status ws)))
                                           (get-parsed-response false)))
                     (swap! state update :server-response
                       assoc :error-message status-text)))})
     (endpoints/get-billing-projects
      (fn [err-text projects]
        (if err-text
          (swap! state update :server-response assoc
                 :error-message err-text :disabled-reason :error)
          (swap! state update :server-response assoc
                 :billing-projects (map #(% "projectName") projects)
                 :disabled-reason (if (empty? projects) :no-billing nil))))))})


(defn- create-breadcrumbs-from-hash [hash]
  (let [segments (split hash #"/")]
    (map-indexed
      (fn [index segment]
        (if (zero? index)
          {:text "Workspaces" :href "#workspaces"}
          {:text (clojure.string/replace (js/decodeURIComponent segment) ":" "/")
           :href (str "#" (join "/" (subvec segments 0 (inc index))))}))
      segments)))


(react/defc Page
  {:render
   (fn [{:keys [props]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           selected-ws-id (common/get-id-from-nav-segment (:segment nav-context))]
       [:div {:style {:marginTop "2em"}}
        (if selected-ws-id
          [:div {}
           [:div {:style {:margin "1em"}}
            [:span {:style {:fontSize "180%"}}
             [comps/Breadcrumbs {:crumbs (create-breadcrumbs-from-hash (:hash nav-context))}]]]
           [WorkspaceDetails {:key selected-ws-id
                              :workspace-id selected-ws-id
                              :nav-context nav-context
                              :on-delete #(nav/back nav-context)
                              :on-clone #(nav/navigate (:nav-context props) %)}]]
          [WorkspaceList {:nav-context nav-context}])]))})
