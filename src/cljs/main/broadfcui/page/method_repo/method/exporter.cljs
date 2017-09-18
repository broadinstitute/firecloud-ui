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


(react/defc MethodExporter
  {:render
   (fn [{:keys [props this]}]
     (let [{:keys [method-name dismiss]} props]
       [modals/OKCancelForm
        {:header (str "Export " method-name " to Workspace")
         :content (react/create-element
                   [:div {:style {:width "80vw"}}
                    [:div {:style {:fontSize "120%" :marginBottom "0.5rem"}} "Select Method Configuration"]
                    [SplitPane
                     {:left (this :-render-config-table)
                      :right (this :-render-preview)
                      :initial-slider-position 850
                      :slider-padding "0.5rem"}]])
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
   :-render-config-table
   (fn [{:keys [props state]}]
     (let [{:keys [configs configs-error]} @state]
       (cond configs-error (style/create-server-error-message configs-error)
             configs (configs/render-config-table {:configs configs
                                                   :make-config-link-props (fn [config]
                                                                             {:onClick #(swap! state assoc :selected-config config)})})
             :else [comps/Spinner {:text "Loading Method Configurations..."}])))
   :-render-preview
   (fn [{:keys [props state]}]
     (let [{:keys [selected-config]} @state]
       (if selected-config
         [:div {} "Hello, config!"]
         [:div {:style {:position "relative" :backgroundColor "white" :height "100%"}}
          (style/center {:style {:textAlign "center"}} "Select a Configuration to Preview")])))})
