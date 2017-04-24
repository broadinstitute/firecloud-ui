(ns broadfcui.page.workspaces-list
  (:require
    [clojure.string :refer [split join split-lines]]
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.filter :as filter]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.common.table.table :refer [Table]]
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
           {:keys [status disabled? hover-text workspace-id]} data]
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
           {:keys [status restricted? disabled? hover-text workspace-id]} data
           {:keys [namespace name]} workspace-id
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
        [:div {:style {:paddingLeft 24}}
         [:div {:style {:fontSize "80%"}} namespace]
         [:div {:style {:fontWeight 600}} name]]]))})

(defn- get-workspace-name-string [column-data]
  (str (get-in column-data [:workspace-id :namespace]) "/" (get-in column-data [:workspace-id :name])))

(defn- get-workspace-description [ws]
  (not-empty (get-in ws [:workspace :attributes :description])))

;; An obnoxious amount of effort due to "PROJECT_OWNER" vs. "NO ACCESS"
(defn- prettify [s]
  (as-> s $
        (clojure.string/replace $ "_" " ")
        (split $ #"\b")
        (map clojure.string/capitalize $)
        (join $)))


(def ^:private access-levels ["PROJECT_OWNER" "OWNER" "WRITER" "READER" "NO ACCESS"])

(def ^:private table-filters
  [{:title "Status"
    :options ["Complete" "Running" "Exception"]
    :render identity
    :predicate (fn [ws option] (= (:status ws) option))}
   {:title "Access"
    :options access-levels
    :render prettify
    :predicate (fn [ws option] (= (:accessLevel ws) option))}
   {:title "Publishing"
    :options [true false]
    :render #(if % "Published" "Un-published")
    :predicate (fn [ws option] (= option (boolean (get-in ws [:workspace :attributes :library:published]))))}
   {:title "TCGA Access"
    :options [:open :protected]
    :render #(if (= % :open) "TCGA Open Access" "TCGA Protected Access")
    :predicate (fn [ws option] (and (= (config/tcga-namespace) (get-in ws [:workspace :namespace]))
                                    ((if (= option :open) not identity) (get-in ws [:workspace :realm]))))}])

(def ^:private persistence-key "workspace-table-types")
(def ^:private VERSION 2)


(defn- union-tags [workspaces]
  (->> workspaces
       (keep #(some-> % :workspace :attributes :tag:tags :items set))
       (apply clojure.set/union)))


(react/defc WorkspaceTable
  {:get-initial-state
   (fn []
     (persistence/try-restore
      {:key persistence-key
       :initial (fn [] {:v VERSION
                        :filters-expanded? true
                        :filters (into {"Tags" []} (map (fn [{:keys [title]}] [title #{}])
                                                        table-filters))})
       :validator (comp (partial = VERSION) :v)}))
   :component-will-mount
   (fn [{:keys [props locals]}]
     (swap! locals assoc
            :tags (union-tags (:workspaces props))
            :total-count (count (:workspaces props))))
   :render
   (fn [{:keys [props state this locals]}]
     (let [{:keys [nav-context]} props
           {:keys [filters-expanded?]} @state]
       [Table
        {:persistence-key "workspace-table" :v 2
         :data (this :-filter-workspaces) :total-count (:total-count @locals)
         :body
         {:columns
          (let [column-data (fn [ws]
                              (let [disabled? (= (:accessLevel ws) "NO ACCESS")]
                                {:workspace-id (select-keys (:workspace ws) [:namespace :name])
                                 :href (let [x (:workspace ws)] (str (:namespace x) ":" (:name x)))
                                 :status (:status ws)
                                 :disabled? disabled?
                                 :hover-text (when disabled? (if (get-in ws [:workspace :isProtected])
                                                               dbGap-disabled-text
                                                               non-dbGap-disabled-text))
                                 :restricted? (get-in ws [:workspace :realm])}))]
            ;; All of this margining is terrible, but since this table
            ;; will be redesigned soon I'm leaving it as-is.
            [{:id "Status" :header [:span {:style {:marginLeft 7}} "Status"]
              :sortable? false :resizable? false :filterable? false :initial-width row-height-px
              :column-data column-data :as-text :status
              :render (fn [data] [StatusCell (utils/restructure data nav-context)])}
             {:id "Workspace" :header [:span {:style {:marginLeft 24}} "Workspace"]
              :initial-width 300
              :column-data column-data :as-text get-workspace-name-string
              :sort-by #(mapv clojure.string/lower-case (replace (:workspace-id %) [:namespace :name]))
              :render (fn [data] [WorkspaceCell (utils/restructure data nav-context)])}
             {:id "Description" :header [:span {:style {:marginLeft 14}} "Description"]
              :initial-width 350
              :column-data get-workspace-description
              :render (fn [description]
                        [:div {:style {:paddingLeft 14}}
                         (if description
                           (-> description split-lines first)
                           [:span {:style {:fontStyle "italic"}}
                            "No description provided"])])}
             {:id "Last Modified" :header [:span {:style {:marginLeft 14}} "Last Modified"]
              :initial-width 200
              :column-data (comp :lastModified :workspace)
              :render (fn [date]
                        [:div {:style {:paddingLeft 14}} (common/format-date date common/short-date-format)])
              :as-text common/format-date}
             {:id "Access Level" :header [:span {:style {:marginLeft 14}} "Access Level"]
              :initial-width 132 :resizable? false
              :column-data :accessLevel
              :sort-by (zipmap access-levels (range)) :sort-initial :asc
              :render (fn [access-level]
                        [:div {:style {:paddingLeft 14}}
                         (prettify access-level)])}])
          :behavior {:reorderable-columns? false}
          :style {:header-row {:color (:text-lighter style/colors) :fontSize "90%"}
                  :header-cell {:padding "0.4rem 0"}
                  :resize-tab (table-style/tab :line-default)
                  :body {:border style/standard-line}
                  :body-row (fn [{:keys [index]}]
                              (merge {:alignItems "center"}
                                     (when (pos? index)
                                       {:borderTop style/standard-line})))
                  :cell table-style/clip-text}}
         :toolbar {:style {:display "initial"}
                   :filter-bar {:style {:float "left"}
                                :inner {:width 300}}
                   :items [[:div {:style {:float "right"}}
                            [create/Button (select-keys props [:nav-context :billing-projects :disabled-reason])]]
                           [:div {:style {:clear "left" :float "left" :marginTop "0.5rem"}}
                            (style/create-link {:text (if filters-expanded? "Collapse" "Show additional filters")
                                                :onClick #(swap! state update :filters-expanded? not)})]
                           [:div {:style {:clear "both"}}]
                           (when filters-expanded?
                             (this :-side-filters))]}
         :paginator {:style {:clear "both"}}}]))
   :component-did-update
   (fn [{:keys [state]}]
     (persistence/save {:key persistence-key :state state}))
   :-side-filters
   (fn [{:keys [state refs locals]}]
     (let [{:keys [filters]} @state]
       (apply
        filter/area
        {:style {:float "left" :margin "0 1rem 1rem 0" :width 175}}
        (filter/section
         {:title "Tags"
          :content (react/create-element
                    [comps/TagAutocomplete {:ref "tag-autocomplete"
                                            :tags (filters "Tags")
                                            :data (:tags @locals)
                                            :show-counts? false :allow-new? false
                                            :on-change #(swap! state update :filters assoc "Tags" %)}])
          :on-clear #((@refs "tag-autocomplete") :set-tags [])})
        (map (fn [{:keys [title options render]}]
               (filter/section
                {:title title
                 :content (filter/checkboxes
                           {:items (map (fn [option]
                                          {:item option
                                           :render render})
                                        options)
                            :checked-items (get-in @state [:filters title])
                            :on-change (fn [item checked?]
                                         (swap! state update-in [:filters title]
                                                (if checked? conj disj) item))})
                 :on-clear #(swap! state update-in [:filters title] empty)}))
             table-filters))))
   :-filter-workspaces
   (fn [{:keys [props state]}]
     (let [{:keys [workspaces]} props
           {:keys [filters]} @state
           checkbox-filters (map (fn [{:keys [title predicate]}]
                                   (let [selected (filters title)]
                                     (if (empty? selected)
                                       (constantly true)
                                       (apply some-fn (map (fn [option]
                                                             (fn [ws] (predicate ws option)))
                                                           selected)))))
                                 table-filters)
           selected-tags (filters "Tags")
           tag-filter (if (empty? selected-tags)
                        (constantly true)
                        (fn [ws]
                          (let [ws-tags (set (get-in ws [:workspace :attributes :tag:tags :items]))]
                            (every? (partial contains? ws-tags) selected-tags))))]
       (filter (apply every-pred tag-filter checkbox-filters) workspaces)))})


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
