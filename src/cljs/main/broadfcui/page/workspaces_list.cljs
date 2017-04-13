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
    [broadfcui.persistence :as persistence]
    [broadfcui.utils :as utils]
    ))


(def row-height-px 56)

(def dbGap-disabled-text
  (str "This workspace contains controlled access data. You can only access the contents of this workspace if you are "
       "dbGaP authorized for TCGA data and have linked your FireCloud account to your eRA Commons account."))

(def non-dbGap-disabled-text "This workspace is protected. To access the data, please contact help@firecloud.org.")

(react/defc StatusCell
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [data]} props
           {:keys [href status disabled? hover-text workspace-id]} data]
       [:a {:href (if disabled?
                    "javascript:;"
                    (nav/get-link :workspace-summary workspace-id))
            :style {:display "block" :position "relative"
                    :backgroundColor (if disabled? (:disabled-state style/colors) (style/color-for-status status))
                    :margin "2px 0 2px 2px" :height (- row-height-px 4)
                    :cursor (when disabled? "default")}
            :title hover-text}
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
     (let [{:keys [data]} props
           {:keys [href status restricted? disabled? hover-text workspace-id]} data
           color (style/color-for-status status)]
       [:a {:href (if disabled?
                    "javascript:;"
                    (nav/get-link :workspace-summary workspace-id))
            :style {:display "flex" :alignItems "center"
                    :backgroundColor (if disabled? (:disabled-state style/colors) color)
                    :color "white" :textDecoration "none"
                    :cursor (when disabled? "default")
                    :height (- row-height-px 4)
                    :margin "2px 0"}
            :title hover-text}
        (when restricted?
          [:span {:style {:display "block" :position "relative"}}
           [:span {:style {:display "block" :position "absolute" :left -17 :top -9
                           :width (- row-height-px 4) :padding "4px 0"
                           :backgroundColor "white" :color "#666" :fontSize "xx-small"
                           :transform "rotate(-90deg)"}}
            "RESTRICTED"]])
        [:span {:style {:paddingLeft 24 :fontWeight 600}}
         (:name data)]]))})

(defn- get-workspace-name-string [ws]
  (str (get-in ws [:workspace :namespace]) "/" (get-in ws [:workspace :name])))

(defn- get-workspace-description [ws]
  (not-empty (get-in ws [:workspace :attributes :description])))

(defn- get-max-length [func workspaces]
  (->> workspaces (map func) (map count) (apply max)))

(def ^:private access-types ["Project Owner" "Owner" "Writer" "Reader" "No Access"])
(def ^:private access-types-defaults [true true true true true])
(def ^:private access-predicates
  {"Project Owner" #(= "PROJECT_OWNER" (:accessLevel %))
   "Owner" #(= "OWNER" (:accessLevel %))
   "Writer" #(= "WRITER" (:accessLevel %))
   "Reader" #(= "READER" (:accessLevel %))
   "No Access" #(= "NO ACCESS" (:accessLevel %))})

(def ^:private dataset-types ["Un-published" "Published"])
(def ^:private dataset-types-defaults [true false])
(def ^:private dataset-predicates
  (let [published? #(get-in % [:workspace :attributes :library:published])]
    {"Published" published?
     "Un-published" (complement published?)}))

(def ^:private realm-types ["TCGA Open Access" "TCGA Protected Access"])
(def ^:private realm-types-defaults [false false])
(def ^:private realm-predicates
  (let [tcga? #(= (config/tcga-namespace) (get-in % [:workspace :namespace]))
        inRealm? #(get-in % [:workspace :realm])]
    {"TCGA Open Access" (every-pred tcga? (complement inRealm?))
     "TCGA Protected Access" (every-pred tcga? inRealm?)
     ; this pred is used when neither visible option is selected to allow non TCGA workspaces to be shown
     ; it is not intended to be displayed as an option
     "TCGA None" (complement tcga?)}))

(def ^:private persistence-key "workspace-table-types")
(def ^:private VERSION 1)


(react/defc WorkspaceTable
  {:get-initial-state
   (fn []
     (persistence/try-restore
      {:key persistence-key
       :initial (fn [] {:v VERSION :selected-types (merge (zipmap access-types access-types-defaults)
                                                          (zipmap dataset-types dataset-types-defaults)
                                                          (zipmap realm-types realm-types-defaults))})
       :validator (comp (partial = VERSION) :v)}))
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
                                (map checkbox dataset-types)
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
                    [create/Button (select-keys props [:billing-projects :disabled-reason])])
          :filter-groups [{:text "All" :pred (constantly true)}
                          {:text "Complete" :pred #(= "Complete" (:status %))}
                          {:text "Running" :pred #(= "Running" (:status %))}
                          {:text "Exception" :pred #(= "Exception" (:status %))}]
          :columns
          [{:sort-by :none :filter-by :none :starting-width row-height-px :resizable? false
            :header [:div {:style {:marginLeft -6}} "Status"] :header-key "Status"
            :as-text :status
            :content-renderer (fn [data] [StatusCell {:data data}])}
           {:as-text :name :sort-by :text
            :header [:span {:style {:marginLeft 10}} "Workspace"] :header-key "Workspace"
            :starting-width (min 500 (* max-workspace-name-length 10))
            :content-renderer (fn [data] [WorkspaceCell {:data data}])}
           {:header "Description" :starting-width (max 200 (min 500 (* max-description-length 10)))
            :content-renderer (fn [description]
                                [:div {:style {:paddingLeft 14}}
                                 (if description (-> description split-lines first)
                                                 [:span {:style {:fontStyle "italic"}}
                                                  "No description provided"])])}
           {:as-text common/format-date
            :header "Last Modified Date" :starting-width 300
            :content-renderer (fn [date]
                                [:div {:style {:paddingLeft 14}} (common/format-date date)])}
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
          :data (let [somepred (fn [preds]
                                 (->> (merge preds (:selected-types @state))
                                      (keep (fn [[k v]] (when (and (not (false? v)) (some? (preds k))) k)))
                                      (map preds)
                                      (cons (constantly false)) ;; keeps (apply some-fn) from bombing when the list is empty
                                      (apply some-fn)))]
                  (filter
                   (every-pred
                    (somepred access-predicates)
                    (somepred dataset-predicates)
                    (somepred realm-predicates))
                   (:workspaces props)))
          :->row (fn [ws]
                   (let [ws-name (get-workspace-name-string ws)
                         ws-href (let [x (:workspace ws)] (str (:namespace x) ":" (:name x)))
                         disabled? (= (:accessLevel ws) "NO ACCESS")
                         hover-text (when disabled? (if (get-in ws [:workspace :isProtected])
                                                      dbGap-disabled-text
                                                      non-dbGap-disabled-text))]
                     [{:name ws-name :href ws-href :status (:status ws) :disabled? disabled? :hover-text hover-text}
                      {:name ws-name :href ws-href :status (:status ws) :disabled? disabled?
                       :hover-text hover-text
                       :workspace-id (select-keys (:workspace ws) [:namespace :name])
                       :restricted? (get-in ws [:workspace :realm])}
                      (get-workspace-description ws)
                      (get-in ws [:workspace :lastModified])
                      (:accessLevel ws)
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
         (some nil? [workspaces]) [comps/Spinner {:text "Loading workspaces..."}]
         :else
         [:div {:style {:padding "0 1rem"}}
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
                                           (get-parsed-response)))
                     (swap! state update :server-response
                       assoc :error-message status-text)))})
     (endpoints/get-billing-projects
      (fn [err-text projects]
        (if err-text
          (swap! state update :server-response assoc
                 :error-message err-text :disabled-reason :error)
          (swap! state update :server-response assoc
                 :billing-projects (map :projectName projects)
                 :disabled-reason (if (empty? projects) :no-billing nil))))))})

(react/defc Page
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:marginTop "1.5rem"}}
      [WorkspaceList]])})

(defn add-nav-paths []
  (nav/defredirect {:regex #"workspaces" :make-path (fn [] "")})
  (nav/defpath
    :workspaces
    {:component Page
     :regex #""
     :make-props (fn [] {})
     :make-path (fn [] "")}))
