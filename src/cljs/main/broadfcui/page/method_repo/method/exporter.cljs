(ns broadfcui.page.method-repo.method.exporter
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.input :as input]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.split-pane :refer [SplitPane]]
   [broadfcui.components.workspace-selector :refer [WorkspaceSelector]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.method-repo.method.configs :as configs]
   [broadfcui.net :as net]
   [broadfcui.utils :as utils]
   ))


(react/defc- Preview
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [config config-error]} @state]
       (cond config-error (style/create-server-error-message config-error)
             config [:div {:style {:maxHeight "-webkit-fill-available"}} (configs/render-config-details config)]
             :else [comps/Spinner {:text "Loading Configuration Details..."}])))
   :component-did-mount
   (fn [{:keys [props this]}]
     (this :-load (:preview-config props)))
   :component-will-receive-props
   (fn [{:keys [props next-props this]}]
     (when (not= (:preview-config props) (:preview-config next-props))
       (this :-load (:preview-config next-props))))
   :-load
   (fn [{:keys [state]} config]
     (swap! state dissoc :config :config-error)
     (let [{:keys [namespace name snapshotId]} config]
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-configuration namespace name snapshotId true)
         :on-done (net/handle-ajax-response
                   (fn [{:keys [success? parsed-response]}]
                     (if success?
                       (swap! state assoc :config parsed-response)
                       (swap! state assoc :config-error (:message parsed-response)))))})))})


(react/defc MethodExporter
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [method-name dismiss]} props
           {:keys [configs configs-error selected-config]} @state]
       [modals/OKCancelForm
        {:header (str "Export " method-name " to Workspace")
         :content
         (react/create-element
          (cond configs-error (style/create-server-error-message configs-error)
                selected-config (this :-render-page-2)
                configs (this :-render-page-1)
                :else [comps/Spinner {:text "Loading Method Configurations..."}]))
         :button-bar (cond selected-config (this :-render-button-bar-2)
                           configs (this :-render-button-bar-1))
         :show-cancel? false
         :dismiss dismiss}]))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-agora-method-configs (:method-id props))
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? parsed-response]}]
                   (if success?
                     (let [configs (map #(assoc % :payload (utils/parse-json-string (:payload %) true)) parsed-response)]
                       (swap! state assoc :configs configs))
                     (swap! state assoc :configs-error (:message parsed-response)))))}))
   :-render-page-1
   (fn [{:keys [state]}]
     (let [{:keys [configs preview-config]} @state]
       [:div {:style {:width "80vw" :maxHeight 600}}
        [:div {:style {:fontSize "120%" :marginBottom "0.5rem"}}
         "Select Method Configuration"]
        [SplitPane
         {:left (configs/render-config-table
                 {:configs configs
                  :make-config-link-props (fn [config]
                                            {:onClick #(swap! state assoc :preview-config config)})})
          :right (if preview-config
                   [Preview {:preview-config preview-config}]
                   [:div {:style {:position "relative" :backgroundColor "white" :height "100%"}}
                    (style/center {:style {:textAlign "center"}} "Select a Configuration to Preview")])
          :initial-slider-position 800
          :slider-padding "0.5rem"}]]))
   :-render-button-bar-1
   (fn [{:keys [state]}]
     (let [{:keys [preview-config]} @state]
       (flex/box
        {}
        flex/spring
        [buttons/Button {:type :secondary :text "Use Blank Configuration"
                         :onClick #(swap! state assoc :selected-config :blank)}]
        (flex/strut "1rem")
        [buttons/Button {:text "Use Selected Configuration"
                         :disabled? (when-not preview-config "Select a configuration first")
                         :onClick #(swap! state assoc :selected-config preview-config)}])))
   :-render-page-2
   (fn [{:keys [props state]}]
     (let [{:keys [method-name]} props
           {:keys [selected-config]} @state]
       [:div {:style {:width 550}}
        (style/create-form-label "Name")
        [input/TextField {:style {:width "100%"}
                          :defaultValue (if (= selected-config :blank)
                                          method-name
                                          (:name selected-config))}]
        (style/create-form-label "Destination Workspace")
        [WorkspaceSelector {:filter #(common/access-greater-than-equal-to? (:accessLevel %) "WRITER")
                            :on-select #(swap! state assoc :selected-workspace %)}]]))
   :-render-button-bar-2
   (fn [{:keys [state]}]
     (flex/box
      {:style {:alignItems "center"}}
      (links/create-internal
        {:onClick #(swap! state dissoc :selected-config)}
        [:div {:style {:display "flex" :alignItems "center"}}
         (icons/icon {:style {:fontSize "150%" :marginRight "0.5rem"}} :angle-left)
         "Choose Another Configuration"])
      flex/spring
      [buttons/Button {:text "Export to Workspace"}]))})
