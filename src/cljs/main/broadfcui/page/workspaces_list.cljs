(ns broadfcui.page.workspaces-list
  (:require
    [dmohs.react :as react]
    [clojure.string :as string]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.filter :as filter]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.common.table.table :refer [Table]]
    [broadfcui.config :as config]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.net :as net]
    [broadfcui.page.workspace.create :as create]
    [broadfcui.persistence :as persistence]
    [broadfcui.utils :as utils]
    ))


(def row-height-px 56)

(def tcga-disabled-text
  (str "This workspace contains controlled access data. You can only access the contents of this workspace if you are "
       "dbGaP authorized for TCGA data and have linked your FireCloud account to your eRA Commons account."))

(def non-dbGap-disabled-text "Click to request access.")

(react/defc RequestAuthDomainAccessDialog
  {:get-initial-state
   (fn [{:keys [props state]}]
     {:ws-auth-domains (vec (map
                             (fn [group]
                               {:name group
                                :data {:member? (contains? (:my-auth-domains @state) group)
                                       :requested? false
                                       :requesting? false}})
                             (:ws-auth-domains props)))})
   :render
   (fn [{:keys [state this]}]
     [comps/OKCancelForm
      {:header "Request Access"
       :content
       (react/create-element
        (let [{:keys [my-auth-domains ws-instructions error]} @state]
          (cond
            (not (or (and my-auth-domains ws-instructions) error))
            [comps/Spinner {:text "Loading Authorization Domains..."}]
            error
            (case (:code error)
              (:unknown :parse-error)
              [:div {:style {:color (:exception-state style/colors)}}
               "Error:" [:div {} (:details error)]]
              [comps/ErrorViewer {:error (:details error)}])
            :else
            [:div {:style {:width 750}}
             [:div {} "You cannot access this workspace because it contains restricted data.
                         You need permission from the admin(s) of all of the Authorization Domains
                         protecting the workspace."]
             (let [simple-th (fn [text]
                               [:th {:style {:textAlign "left" :padding "0 0.5rem"
                                             :borderBottom style/standard-line}}
                                text])
                   simple-td (fn [text]
                               [:td {}
                                [:label {:style {:display "block" :padding "1rem 0.5rem"
                                                 :width "33%"}}
                                 text]])
                   instructions (reduce
                                 (fn [m ad]
                                   (assoc m (keyword (:groupName ad)) (:instructions ad))) {}
                                 ws-instructions)]
               [:table {:style {:width "100%" :borderCollapse "collapse"
                                :margin "1em 0 1em 0"}}
                [:thead {}
                 [:tr {}
                  (simple-th "Authorization Domain")
                  (simple-th "Access")
                  (simple-th "")]]
                [:tbody {}
                 (map-indexed (fn [i auth-domain]
                                (let [{:keys [member? requested? requesting?]} (:data auth-domain)
                                      name (:name auth-domain)
                                      instruction ((keyword name) instructions)]
                                  [:tr {}
                                   (simple-td name)
                                   (simple-td (if member? "Yes" "No"))
                                   (if instruction
                                     [:td {:style {:width "34%"}}
                                      (when-not member?
                                        [:div {:style {:fontSize "85%"}}
                                         "Learn how to apply for this Authorization Domain  "
                                         [:a {:href instruction :target "_blank"} "here"] "."])]
                                     [:td {:style {:width "34%"}}
                                      (when-not member?
                                        [:div {}
                                         [comps/Button {:style {:width "125px"}
                                                        :disabled? (or requested? requesting?)
                                                        :text (if requested? "Request Sent" "Request Access")
                                                        :onClick #(react/call :-request-access this name i)}]
                                         (when requesting? [comps/Spinner])
                                         (when requested?
                                           (common/render-info-box
                                            {:text [:div {}
                                                    "Your request has been submitted. When you are granted
                                                    access, the " [:strong {} "Access Level"] " displayed on
                                                     the Workspace list will be updated."]}))])])]))
                              (:ws-auth-domains @state))]])])))}])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :-load-groups this)
     (react/call :-get-access-instructions this))
   :-load-groups
   (fn [{:keys [state]}]
     (endpoints/get-groups
      (fn [err-text groups]
        (if err-text
          (swap! state assoc :error-message err-text)
          (swap! state assoc :my-auth-domains (map :groupName groups))))))
   :-get-access-instructions
   (fn [{:keys [props state]}]
     (endpoints/get-ws-access-instructions (:workspace-id props)
                                           (fn [err-text instructions]
                                             (if err-text
                                               (swap! state assoc :error-message err-text)
                                               (swap! state assoc :ws-instructions instructions)))))
   :-request-access
   (fn [{:keys [state]} group-name group-index]
     (swap! state update-in [:ws-auth-domains group-index :data] assoc :requesting? true)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/request-group-access group-name)
       :on-done (fn []
                  (swap! state update-in [:ws-auth-domains group-index :data]
                         assoc :requesting? false :requested? true))}))})

(defn- get-workspace-name-string [column-data]
  (str (get-in column-data [:workspace-id :namespace]) "/" (get-in column-data [:workspace-id :name])))

(defn- get-workspace-description [ws]
  (not-empty (get-in ws [:workspace :attributes :description])))


(def ^:private access-levels ["PROJECT_OWNER" "OWNER" "WRITER" "READER" "NO ACCESS"])

(def ^:private table-filters
  [{:title "Status"
    :options ["Complete" "Running" "Exception"]
    :render identity
    :predicate (fn [ws option] (= (:status ws) option))}
   {:title "Access"
    :options access-levels
    :render style/prettify-access-level
    :predicate (fn [ws option] (= (:accessLevel ws) option))}
   {:title "Publishing"
    :options [true false]
    :render #(if % "Published" "Un-published")
    :predicate (fn [ws option] (= option (boolean (get-in ws [:workspace :attributes :library:published]))))}
   {:title "TCGA Access"
    :options [:open :protected]
    :render #(if (= % :open) "TCGA Open Access" "TCGA Protected Access")
    :predicate (fn [ws option]
                 (and (= (config/tcga-namespace) (get-in ws [:workspace :namespace]))
                      ((if (= option :open) not identity) (get-in ws [:workspace :authorizationDomain]))))}])

(def ^:private persistence-key "workspace-table-types")
(def ^:private VERSION 2)


(defn- union-tags [workspaces]
  (->> workspaces
       (keep #(some-> % :workspace :attributes :tag:tags :items set))
       (apply clojure.set/union)))


(defn- push-request-access-modal [workspace-id auth-domains]
  (modal/push-modal
   [RequestAuthDomainAccessDialog
    {:workspace-id workspace-id
     :ws-auth-domains auth-domains}]))

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
     (let [{:keys [filters-expanded?]} @state]
       [Table
        {:persistence-key "workspace-table" :v 2
         :data (this :-filter-workspaces) :total-count (:total-count @locals)
         :body
         {:columns
          (let [column-data (fn [ws]
                              (let [no-access? (= (:accessLevel ws) "NO ACCESS")
                                    tcga? (and no-access? (= (get-in ws [:workspace :authorizationDomain :membersGroupName])
                                                             config/tcga-authorization-domain))]
                                {:workspace-id (select-keys (:workspace ws) [:namespace :name])
                                 :status (:status ws)
                                 ;; this will very soon return multiple auth domains, so im future-proofing it now
                                 :auth-domains [(get-in ws [:workspace :authorizationDomain :membersGroupName])]
                                 :no-access? no-access?
                                 :access-level (:accessLevel ws)
                                 :hover-text (when no-access?
                                               (if tcga?
                                                 tcga-disabled-text
                                                 non-dbGap-disabled-text))
                                 :restricted? (some? (get-in ws [:workspace :authorizationDomain :membersGroupName]))}))]
            ;; All of this margining is terrible, but since this table
            ;; will be redesigned soon I'm leaving it as-is.
            [{:id "Status" :header [:span {:style {:marginLeft 7}} "Status"]
              :sortable? false :resizable? false :filterable? false :initial-width row-height-px
              :column-data column-data :as-text :status
              :render #(this :-render-status-cell %)}
             {:id "Workspace" :header [:span {:style {:marginLeft 24}} "Workspace"]
              :initial-width 300
              :column-data column-data :as-text get-workspace-name-string
              :sort-by #(mapv clojure.string/lower-case (replace (:workspace-id %) [:namespace :name]))
              :render #(this :-render-workspace-cell %)}
             {:id "Description" :header [:span {:style {:marginLeft 14}} "Description"]
              :initial-width 350
              :column-data get-workspace-description
              :render (fn [description]
                        [:div {:style {:paddingLeft 14}}
                         (if description
                           (-> description string/split-lines first)
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
              :column-data column-data
              :sort-by (zipmap access-levels (range)) :sort-initial :asc
              :render (fn [{:keys [access-level workspace-id auth-domains]}]
                        [:div {:style {:paddingLeft 14}}
                         (if (= access-level "NO ACCESS")
                           (style/create-link {:text (style/prettify-access-level access-level)
                                               :onClick #(push-request-access-modal workspace-id auth-domains)})
                           (style/prettify-access-level access-level))])}])
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
                   :items (constantly
                           [[:div {:style {:float "right"}}
                             [create/Button (select-keys props [:nav-context :billing-projects :disabled-reason])]]
                            [:div {:style {:clear "left" :float "left" :marginTop "0.5rem"}}
                             (style/create-link {:text (if filters-expanded? "Collapse" "Show additional filters")
                                                 :onClick #(swap! state update :filters-expanded? not)})]
                            [:div {:style {:clear "both"}}]
                            (when filters-expanded?
                              (this :-render-side-filters))])}
         :paginator {:style {:clear "both"}}}]))
   :component-did-update
   (fn [{:keys [state]}]
     (persistence/save {:key persistence-key :state state}))
   :-render-status-cell
   (fn [_ {:keys [status no-access? hover-text workspace-id auth-domains]}]
     [:a {:href (if no-access?
                  "javascript:;"
                  (nav/get-link :workspace-summary workspace-id))
          :onClick (if no-access? #(push-request-access-modal workspace-id auth-domains))
          :style {:display "block" :position "relative"
                  :backgroundColor (if no-access?
                                     (:disabled-state style/colors)
                                     (style/color-for-status status))
                  :margin "2px 0 2px 2px" :height (- row-height-px 4)}
          :title hover-text}
      [:span {:style {:position "absolute" :top 0 :right 0 :bottom 0 :left 0
                      :backgroundColor "rgba(0,0,0,0.2)"}}]
      (style/center {}
                    (case status
                      "Complete" [icons/CompleteIcon]
                      "Running" [icons/RunningIcon]
                      "Exception" [icons/ExceptionIcon]))])
   :-render-workspace-cell
   (fn [_ {:keys [status restricted? no-access? hover-text workspace-id auth-domains]}]
     (let [{:keys [namespace name]} workspace-id
           color (style/color-for-status status)]
       [:a {:href (if no-access?
                    "javascript:;"
                    (nav/get-link :workspace-summary workspace-id))
            :onClick (if no-access? #(push-request-access-modal workspace-id auth-domains))
            :style {:display "flex" :alignItems "center"
                    :backgroundColor (if no-access? (:disabled-state style/colors) color)
                    :color "white" :textDecoration "none"
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
         [:div {:style {:fontWeight 600}} name]]]))
   :-render-side-filters
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
     (let [{:keys [server-response]} @state
           workspaces (map
                       (fn [ws] (assoc ws :status (common/compute-status ws)))
                       (get-in server-response [:workspaces-response :parsed-response]))
           {:keys [billing-projects disabled-reason]} server-response]
       (net/render-with-ajax
        (:workspaces-response server-response)
        (fn []
          [:div {:style {:padding "0 1rem"}}
           [WorkspaceTable
            (assoc props
              :workspaces workspaces
              :billing-projects billing-projects
              :disabled-reason disabled-reason)]])
        {:loading-text "Loading workspaces..."
         :rephrase-error #(get-in % [:parsed-response :workspaces :error-message])})))
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
      {:endpoint endpoints/list-workspaces
       :on-done (net/handle-ajax-response
                 #(swap! state update :server-response assoc :workspaces-response %))})
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
   (fn []
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
