(ns broadfcui.page.method-repo.method-repo-table
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.method-repo.create-method :refer [CreateMethodDialog]]
   [broadfcui.page.method-repo.methods-configs-acl :as mca]
   [broadfcui.page.method-repo.method.details :refer [MethodDetails]]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   ))


(defn- process-methods [methods]
  (let [user-email (user/get-email)]
    (map (fn [{:keys [public managers namespace name] :as method}]
           (assoc method
             :mine? (or (not public)
                        (contains? (set managers) user-email))
             :method-id (utils/restructure namespace name)))
         methods)))


(defn- split-by-name [parsed]
  (let [split (group-by #(contains? % :name) parsed)]
    {:with-name (set (split true))
     :ns-only (set (map :namespace (split false)))}))


(react/defc MethodRepoTable
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [methods error editing-namespace
                   featured-methods featured-namespaces
                   certified-methods certified-namespaces]} @state
           {:keys [workspace-id nav-method]} props]
       [:div {}
        (when (:creating? @state)
          [CreateMethodDialog
           {:dismiss #(swap! state dissoc :creating?)
            :on-created (fn [_ method-id]
                          (nav/go-to-path :method-summary method-id))}])
        (when editing-namespace
          [mca/AgoraPermsEditor
           {:dismiss #(swap! state dissoc :editing-namespace)
            :save-endpoint (endpoints/post-agora-namespace-acl editing-namespace false)
            :load-endpoint (endpoints/get-agora-namespace-acl editing-namespace false)
            :entityType "Namespace" :entityName editing-namespace
            :title (str "Namespace " editing-namespace)}])
        (cond error [comps/ErrorViewer {:error error}]
              (not methods) (spinner "Loading...")
              :else
              [Table {:data-test-id "methods-table"
                      :persistence-key "method-repo-table2" :v 3
                      :data methods
                      :style {:content {:paddingLeft "1rem" :paddingRight "1rem"}}
                      :tabs {:style {:margin "-0.6rem -1rem 0.3rem" :padding "0 1rem"
                                     :backgroundColor (:background-light style/colors)}
                             :items [{:label "My Methods"
                                      :predicate :mine?}
                                     {:label "Public Methods"
                                      :predicate :public}
                                     {:label "Featured Methods"
                                      :predicate (fn [{:keys [namespace method-id]}]
                                                   (or (contains? featured-namespaces namespace)
                                                       (utils/tolerant-contains? featured-methods method-id)))}]}
                      :body {:behavior {:reorderable-columns? false}
                             :style (utils/deep-merge table-style/table-light
                                                      {:body {:fontWeight "initial" :fontSize "120%"
                                                              :border style/standard-line}
                                                       :body-row (fn [{:keys [index]}]
                                                                   {:borderTop (when (pos? index) style/standard-line)
                                                                    :alignItems "center"})})
                             :columns
                             [{:header "Certified" :initial-width 90
                               :filterable? false :sortable? true :resizable? false
                               :column-data
                               (fn [{:keys [namespace method-id]}]
                                 (or (contains? certified-namespaces namespace)
                                     (utils/tolerant-contains? certified-methods method-id)))
                               :as-text (fn [certified?] (if certified? "Certified" "Not Certified"))
                               :sort-by not
                               :render
                               (fn [certified?]
                                 (when certified? (icons/certified-icon {:style {:display "block" :margin "auto"}})))}
                              {:header "Method" :initial-width 300
                               :column-data :method-id
                               :as-text (fn [{:keys [namespace name]}] (str namespace "/" name))
                               :sort-by (comp string/lower-case :name)
                               :render
                               (fn [{:keys [namespace name] :as method-id}]
                                 (links/create-internal
                                   (utils/deep-merge
                                    {:data-test-id (str "method-link-" namespace "-" name)
                                     :style {:display "block" :marginTop -4}}
                                    (if workspace-id
                                      {:onClick #(nav-method
                                                  {:label (str namespace "/" name)
                                                   :component MethodDetails
                                                   :props (utils/restructure method-id nav-method workspace-id)})}
                                      {:href (nav/get-link :method-loader method-id)}))
                                   [:span
                                    {:className (when-not workspace-id "underline-on-hover")
                                     :style {:fontSize "80%" :color "black"}
                                     :onClick (when-not workspace-id
                                                (fn [e]
                                                  (.preventDefault e)
                                                  (swap! state assoc :editing-namespace namespace)))}
                                    namespace]
                                   [:div {:style {:fontWeight 600}} name]))}
                              {:header "Synopsis" :initial-width 475
                               :column-data :synopsis
                               :sort-by string/lower-case}
                              {:header "Owners" :initial-width 225
                               :column-data :managers
                               :as-text (partial string/join ", ")}
                              {:header "Snapshots" :initial-width 94 :filterable? false
                               :column-data :numSnapshots}
                              {:header "Configurations" :initial-width :auto :filterable? false
                               :column-data :numConfigurations}]}
                      :toolbar {:filter-bar {:inner {:width 300}}
                                :style {:alignItems "flex-start"
                                        :padding "1rem" :margin 0
                                        :backgroundColor (:background-light style/colors)}
                                :get-items
                                (constantly
                                 (when-not workspace-id
                                   [[buttons/Button {:data-test-id "create-method-button"
                                                     :style {:marginLeft "auto"}
                                                     :text "Create New Method..."
                                                     :icon :add-new
                                                     :onClick #(swap! state assoc :creating? true)}]]))}}])]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :-refresh))
   :-refresh
   (fn [{:keys [state]}]
     (swap! state dissoc :methods :error)
     (endpoints/call-ajax-orch
      {:endpoint endpoints/list-method-definitions
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (swap! state assoc :methods (process-methods (get-parsed-response)))
                    (swap! state assoc :error (get-parsed-response false))))})
     (ajax/get-google-bucket-file
      "featured-methods"
      (fn [parsed]
        (let [{:keys [with-name ns-only]} (split-by-name parsed)]
          (swap! state assoc :featured-methods with-name :featured-namespaces ns-only))))
     (ajax/get-google-bucket-file
      "certified-methods"
      (fn [parsed]
        (let [{:keys [with-name ns-only]} (split-by-name parsed)]
          (swap! state assoc :certified-methods with-name :certified-namespaces ns-only)))))})
