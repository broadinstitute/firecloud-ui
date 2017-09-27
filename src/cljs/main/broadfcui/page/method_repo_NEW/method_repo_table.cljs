(ns broadfcui.page.method-repo-NEW.method-repo-table
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.method-repo.create-method :refer [CreateMethodDialog]]
   [broadfcui.page.method-repo.methods-configs-acl :as mca]
   [broadfcui.utils :as utils]
   ))


(react/defc MethodRepoTable
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [methods error editing-namespace]} @state
           {:keys [allow-modals? make-method-clicked-props]} props]
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
              (not methods) [comps/Spinner {:text "Loading..."}]
              :else
              [Table {:data-test-id "methods-table"
                      :persistence-key "method-repo-table2" :v 1
                      :data methods
                      :style {:content {:paddingLeft "1rem" :paddingRight "1rem"}}
                      :tabs {:style {:margin "-0.6rem -1rem 0.3rem" :padding "0 1rem"
                                     :backgroundColor (:background-light style/colors)}
                             :items [{:label "Public Methods"
                                      :predicate :public}
                                     {:label "My Methods"
                                      :predicate (fn [method]
                                                   (or (not (:public method))
                                                       (contains? (set (:managers method)) (utils/get-user-email))))}]}
                      :body {:behavior {:reorderable-columns? false}
                             :style (utils/deep-merge table-style/table-light
                                                      {:body {:fontWeight "initial" :fontSize "120%"
                                                              :border style/standard-line}
                                                       :body-row (fn [{:keys [index]}]
                                                                   {:borderTop (when (pos? index) style/standard-line)
                                                                    :alignItems "center"})})
                             :columns
                             [{:header "Method" :initial-width 300
                               :column-data (juxt :namespace :name)
                               :as-text (partial string/join "/")
                               :sort-by (comp string/lower-case second)
                               :render
                               (fn [[namespace name]]
                                 (links/create-internal
                                  (utils/deep-merge
                                   {:data-test-id (str "method-link-" namespace "-" name)
                                    :style {:display "block" :marginTop -4}}
                                   (make-method-clicked-props (utils/restructure namespace name)))
                                  [:span
                                   {:className (when allow-modals? "underline-on-hover")
                                    :style {:fontSize "80%" :color "black"}
                                    :onClick (when allow-modals?
                                               (fn [e]
                                                 (.preventDefault e)
                                                 (swap! state assoc :editing-namespace namespace)))}
                                   namespace]
                                  [:div {:style {:fontWeight 600}} name]))}
                              {:header "Synopsis" :initial-width 700
                               :column-data :synopsis
                               :sort-by string/lower-case}
                              {:header "Owners" :initial-width 175
                               :column-data :managers
                               :as-text (partial string/join ", ")}
                              {:header "Snapshots" :initial-width 94 :filterable? false
                               :column-data :numSnapshots}
                              {:header "Configurations" :initial-width 117 :filterable? false
                               :column-data :numConfigurations}]}
                      :toolbar {:filter-bar {:inner {:width 300}}
                                :style {:alignItems "flex-start"
                                        :padding "1rem" :margin 0
                                        :backgroundColor (:background-light style/colors)}
                                :get-items
                                (constantly
                                 [[buttons/Button {:data-test-id "create-method-button"
                                                   :style {:marginLeft "auto"}
                                                   :text "Create New Method..."
                                                   :icon :add-new
                                                   :onClick #(swap! state assoc :creating? true)}]])}}])]))
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
                    (swap! state assoc :methods (get-parsed-response))
                    (swap! state assoc :error (get-parsed-response false))))}))})
