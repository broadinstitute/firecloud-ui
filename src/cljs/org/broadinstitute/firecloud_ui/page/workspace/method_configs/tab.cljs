(ns org.broadinstitute.firecloud-ui.page.workspace.method-configs.tab
  (:require
    [dmohs.react :as react]
    clojure.string
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.page.method-config-importer :refer [MethodConfigImporter]]
    [org.broadinstitute.firecloud-ui.page.workspace.method-configs.method-config-editor :refer [MethodConfigEditor]]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- config->id [config]
  {:namespace (config "namespace") :name (config "name")})


(react/defc MethodConfigurationsList
  {:reload
   (fn [{:keys [state this]}]
     (swap! state dissoc :server-response)
     (react/call :load this))
   :render
   (fn [{:keys [props state]}]
     (let [server-response (:server-response @state)
           {:keys [configs error-message]} server-response]
       (cond
         error-message (style/create-server-error-message error-message)
         configs
         [table/Table
          {:empty-message "There are no method configurations to display."
           :toolbar (fn [built-in]
                      [:div {}
                       [:div {:style {:float "left" :margin "5 0 -5 0"}} built-in]
                       [:div {:style {:float "right" :paddingRight "2em"}}
                        [comps/Button {:text "Import Configuration..."
                                       :disabled? (case (:locked? @state)
                                                    nil "Looking up workspace status..."
                                                    true "The workspace is locked"
                                                    false)
                                       :onClick #(modal/push-modal
                                                  [modal/OKCancelForm
                                                   {:header "Import Method Configuration"
                                                    :content
                                                    [:div {:style {:backgroundColor "white" :padding "1ex" :width 1000}}
                                                     [MethodConfigImporter {:workspace-id (:workspace-id props)
                                                                            :after-import (fn [{:keys [config-id]}]
                                                                                            (modal/pop-modal)
                                                                                            ((:on-config-imported props) config-id))}]]}])}]]
                       (common/clear-both)])
           :columns
           [{:header "Name" :starting-width 240 :as-text :name :sort-by :text
             :content-renderer
             (fn [config-id]
               (style/create-link {:text (:name config-id)
                                   :href (nav/create-href (:nav-context props) config-id)}))}
            {:header "Root Entity Type" :starting-width 140}
            {:header "Method" :starting-width 800
             :content-renderer (fn [fields] (apply style/render-entity fields))}]
           :data configs
           :->row (fn [config]
                    [(config->id config)
                     (config "rootEntityType")
                     (mapv #(get-in config ["methodRepoMethod" %])
                           ["methodNamespace" "methodName" "methodVersion"])])}]
         :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading configurations..."}]])))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load this))
   :component-did-update
   (fn [{:keys [this state]}]
     (when (nil? (:server-response @state))
       (react/call :load this)))
   :load
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :locked? (get-in (get-parsed-response) ["workspace" "isLocked"]))
                     (swap! state assoc :error status-text))
                   (swap! state update-in [:load-counter] dec))})
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/list-workspace-method-configs (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :server-response {:configs (vec (get-parsed-response))})
                     (swap! state assoc :server-response {:error-message status-text}))
                   (swap! state update-in [:load-counter] dec))}))})


(react/defc Page
  {:refresh
   (fn [{:keys [props refs]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           selected-method-config-id (common/get-id-from-nav-segment (:segment nav-context))]
       (if selected-method-config-id
         (nav/back nav-context)
         (react/call :reload (@refs "method-config-list")))))
   :render
   (fn [{:keys [props]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           selected-method-config-id (common/get-id-from-nav-segment (:segment nav-context))]
       [:div {:style {:padding "1em"}}
        (if selected-method-config-id
          [MethodConfigEditor {:key selected-method-config-id
                               :config-id selected-method-config-id
                               :workspace-id (:workspace-id props)
                               :bucket-access? (:bucket-access? props)
                               :on-submission-success (:on-submission-success props)
                               :on-rename #(nav/navigate (:nav-context props) (str (:namespace selected-method-config-id) ":" %))
                               :after-delete #(nav/back nav-context)}]
          [MethodConfigurationsList
           {:ref "method-config-list"
            :nav-context nav-context
            :workspace-id (:workspace-id props)
            :on-config-imported #(nav/navigate nav-context %)}])]))})
