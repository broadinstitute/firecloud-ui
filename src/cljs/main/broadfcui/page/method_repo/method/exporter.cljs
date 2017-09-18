(ns broadfcui.page.method-repo.method.exporter
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.split-pane :refer [SplitPane]]
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
   (fn [{:keys [props state]}]
     (let [{:keys [method-name dismiss]} props
           {:keys [configs configs-error preview-config]} @state]
       [modals/OKCancelForm
        {:header (str "Export " method-name " to Workspace")
         :content
         (react/create-element
          (cond configs-error (style/create-server-error-message configs-error)
                configs [:div {:style {:width "80vw" :maxHeight 600}}
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
                           :slider-padding "0.5rem"}]]
                :else [comps/Spinner {:text "Loading Method Configurations..."}]))
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
                     (swap! state assoc :configs-error (:message parsed-response)))))}))})
