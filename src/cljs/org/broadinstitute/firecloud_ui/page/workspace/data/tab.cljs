(ns org.broadinstitute.firecloud-ui.page.workspace.data.tab
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    goog.net.cookies
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :as table-utils]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.data.copy-data-workspaces :as copy-data-workspaces]
    [org.broadinstitute.firecloud-ui.page.workspace.data.entity-selector :refer [EntitySelector]]
    [org.broadinstitute.firecloud-ui.page.workspace.data.import-data :as import-data]
    [org.broadinstitute.firecloud-ui.utils :as utils :refer [access-token]]
    ))


(react/defc DataImporter
  {:render
   (fn [{:keys [state props]}]
     (let [choice? (or (:importing-from-file @state) (:copying-from-workspace @state))]
       [:div {}
        [comps/XButton {:dismiss (:dismiss props)}]
        (when choice?
          [:div {:style {:padding "0.5em"}}
           (style/create-link
             #(swap! state dissoc :importing-from-file :copying-from-workspace)
             (icons/font-icon {:style {:fontSize "70%" :marginRight "1em"}} :angle-left)
             "Back")])
        (when (:importing-from-file @state)
          [:div {:style {:padding "1em"}}
           [import-data/Page (select-keys props [:workspace-id :reload-data-tab])]])
        (when (:copying-from-workspace @state)
          [:div {:style {:padding "1em"}}
           [copy-data-workspaces/Page (select-keys props [:workspace-id :reload-data-tab])]])
        (when-not choice?
          (let [style {:width 240 :margin "auto" :textAlign "center" :cursor "pointer"
                       :backgroundColor (:button-blue style/colors)
                       :color "#fff" :padding "1em" :borderRadius 8}]
            [:div {:style {:padding "2em"}}
             [:div {:onClick #(swap! state assoc :importing-from-file true) :style style}
              "Import from file"]
             [:div {:style {:height "1em"}}]
             [:div {:onClick #(swap! state assoc :copying-from-workspace true)
                    :style style}
              "Copy from another workspace"]]))]))
   :component-did-mount
   (fn []
     (common/scroll-to-top 100))})


(react/defc DataDeleter
  {:render
   (fn [{:keys [state props refs this]}]
     [dialog/Dialog
      {:dismiss-self (:dismiss-self props)
       :width "80%"
       :content
       (react/create-element
         [dialog/OKCancelForm
          {:header "Delete Entities"
           :dismiss-self (:dismiss-self props)
           :content (react/create-element
                      [:div {}
                       (when (:deleting? @state)
                         [comps/Blocker {:banner "Deleting..."}])
                       [EntitySelector
                        {:ref "EntitySelector"
                         :left-text "Workspace Entities" :right-text "Will Be Deleted"
                         :entities (:entity-list props)}]
                       [:div {:style {:textAlign "center" :marginTop "1em"}}
                        (style/create-validation-error-message (:validation-errors @state))
                        [comps/ErrorViewer {:error (:server-error @state)}]]])
           :ok-button [comps/Button
                       {:text "Delete"
                        :onClick #(let [selected-entities (react/call :get-selected-entities (@refs "EntitySelector"))
                                        num (count selected-entities)
                                        msg (if (= num 1)
                                              "Really delete this entity?"
                                              (str "Really delete these " num " entities?"))]
                                    (swap! state dissoc :server-error)
                                    (if (zero? num)
                                      (swap! state assoc :validation-errors
                                        ["Please select one or more entities to delete"])
                                      (when (js/confirm msg)
                                        (react/call :delete this selected-entities))))}]}])}])
   :component-did-mount
   (fn []
     (common/scroll-to-top 100))
   :delete
   (fn [{:keys [props state this]} selected-entities]
     (swap! state assoc :deleting? true :validation-errors nil :server-error nil)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/delete-entities (:workspace-id props))
        :payload {:recursive false ;; TODO implement
                  :entities (map (fn [e] {:entityName (e "name")
                                          :entityType (e "entityType")}) selected-entities)}
        :headers {"Content-Type" "application/json"}
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state dissoc :deleting?)
                   (if success?
                     (do ((:reload props))
                         ((:dismiss-self props)))
                     (swap! state assoc :server-error (get-parsed-response))))}))})


(defn- entity-table [state workspace-id locked? entity-list entity-types initial-entity-type]
  [table/EntityTable
   {:entities entity-list
    :entity-types entity-types
    :initial-entity-type initial-entity-type
    :toolbar (fn [built-in]
               [:div {}
                [:div {:style {:float "left"}} built-in]
                (when-let [selected-entity-type (or (:selected-entity-type @state) initial-entity-type (first entity-types))]
                  [:a {:style {:textDecoration "none" :float "left" :margin "7px 0 0 1em"}
                       :href (str "/service/api/workspaces/" (:namespace workspace-id) "/"
                               (:name workspace-id) "/entities/" selected-entity-type "/tsv")
                       :onClick (fn [] (utils/set-access-token-cookie @access-token))
                       :target "_blank"}
                   (str "Download '" selected-entity-type "' data")])
                [:div {:style {:float "right" :paddingRight "2em"}}
                 [comps/Button {:text "Import Data..."
                                :disabled? (if locked? "This workspace is locked")
                                :onClick #(swap! state assoc :show-import? true)}]]
                [:div {:style {:float "right" :paddingRight "2em"}}
                 [comps/Button {:text "Delete..."
                                :disabled? (if locked? "This workspace is locked")
                                :onClick #(swap! state assoc :show-delete? true)}]]
                (common/clear-both)])
    :on-filter-change #(swap! state assoc :selected-entity-type (nth entity-types %))
    :attribute-renderer (fn [maybe-uri]
                          (if (string? maybe-uri)
                            (if-let [parsed (common/parse-gcs-uri maybe-uri)]
                              [dialog/GCSFilePreviewLink (assoc parsed
                                                           :gcs-uri maybe-uri)]
                              maybe-uri)
                            (table-utils/default-render maybe-uri)))}])


(react/defc WorkspaceData
  {:refresh
   (fn [{:keys [state this]} & [entity-type]]
     (swap! state dissoc :server-response)
     (react/call :load this entity-type))
   :render
   (fn [{:keys [props state refs this]}]
     (let [workspace-id (:workspace-id props)
           server-response (:server-response @state)
           {:keys [server-error locked? entity-list entity-types initial-entity-type]} server-response]
       [:div {:style {:padding "1em"}}
        (when (:deleting? @state)
          [comps/Blocker {:banner "Deleting..."}])
        (when (:show-import? @state)
          [dialog/Dialog {:dismiss-self #(swap! state dissoc :show-import?)
                          :width "80%"
                          :content
                          (react/create-element
                            [DataImporter {:dismiss #(swap! state dissoc :show-import?)
                                           :workspace-id workspace-id
                                           :reload-data-tab (fn [entity-type]
                                                              (swap! state dissoc :entity-list :entity-types)
                                                              (react/call :refresh this entity-type))}])}])
        (when (:show-delete? @state)
          [DataDeleter {:dismiss-self #(swap! state dissoc :show-delete?)
                        :entity-list (:entity-list @state)
                        :workspace-id workspace-id
                        :reload #(react/call :refresh this)}])
        (cond
          server-error
          (style/create-server-error-message server-error)
          (some nil? [locked? entity-list])
          [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading entities..."}]]
          :else
          (entity-table state workspace-id locked? entity-list entity-types initial-entity-type))]))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load this))
   :load
   (fn [{:keys [state props]} & [entity-type]]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state update-in [:server-response]
                       assoc :locked? (get-in (get-parsed-response) ["workspace" "isLocked"]))
                     (swap! state update-in [:server-response]
                       assoc :server-error status-text)))})
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-entities-by-type (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (let [entities (get-parsed-response)]
                       (swap! state update-in [:server-response]
                         assoc :entity-list entities
                               :entity-types (distinct (map #(% "entityType") entities))
                               :initial-entity-type entity-type))
                     (swap! state update-in [:server-response]
                       assoc :server-error status-text)))}))})
