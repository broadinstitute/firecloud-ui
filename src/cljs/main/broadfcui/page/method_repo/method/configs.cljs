(ns broadfcui.page.method-repo.method.configs
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.net :as net]
   [broadfcui.utils :as utils]
   ))

(react/defc ConfigTable
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [make-config-link-props]} props
           {:keys [configs configs-error]} @state]
       (cond
         configs-error [:div {:style {:textAlign "center" :color (:exception-state style/colors)}}
                        "Error loading configs: " configs-error]
         (not configs) [:div {:style {:textAlign "center" :padding "1rem"}}
                        [comps/Spinner {:text "Loading configs..."}]]
         :else
         [Table
          {:data configs
           :body {:empty-message "You don't have access to any published configurations for this method."
                  :style table-style/table-light
                  :behavior {:reorderable-columns? false}
                  :columns [{:header "Configuration" :initial-width 400
                             :as-text (fn [{:keys [name namespace snapshotId]}]
                                        (str namespace "/" name " snapshot " snapshotId))
                             :sort-by :text
                             :render (fn [{:keys [name namespace snapshotId] :as config}]
                                       (links/create-internal
                                        (make-config-link-props config)
                                        (style/render-name-id (str namespace "/" name) snapshotId)))}
                            {:header "Method Snapshot" :initial-width 150 :filterable? false
                             :column-data #(get-in % [:payload :methodRepoMethod :methodVersion]) :sort-by :text}
                            {:header "Synopsis" :initial-width :auto
                             :column-data :synopsis}]}}])))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :refresh))
   :refresh
   (fn [{:keys [props state]}]
     (swap! state dissoc :configs :configs-error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-agora-method-configs (:method-id props))
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? parsed-response]}]
                   (if success?
                     (let [configs (map #(assoc % :payload (utils/parse-json-string (:payload %) true)) parsed-response)]
                       (swap! state assoc :configs configs))
                     (swap! state assoc :configs-error (:message parsed-response)))))}))})

(react/defc ConfigViewer
  {:render
   (fn [{:keys [props state]}]
     [:div {} (str (:config @state))])
   :component-will-mount
   (fn [{:keys [props state]}]
     (swap! state dissoc :config :config-error)
     (let [{:keys [namespace name]} (:config-id props)]
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-configuration namespace name (:config-snapshot-id props))
         :on-done (net/handle-ajax-response
                   (fn [{:keys [success? parsed-response]}]
                     (if success?
                       (swap! state assoc :config parsed-response)
                       (swap! state assoc :config-error (:message parsed-response)))))})))})

(react/defc Configs
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [method-id snapshot-id config-id config-snapshot-id]} props]
       [:div {:style {:margin "2.5rem 1.5rem"}}
        (if config-id
          [ConfigViewer (utils/restructure config-id config-snapshot-id)]
          (let [make-config-link-props (fn [{:keys [namespace name snapshotId]}]
                                         {:href (nav/get-link :method-config-viewer
                                                              method-id snapshot-id
                                                              {:config-ns namespace :config-name name} snapshotId)})]
            [ConfigTable (merge {:ref "table"}
                                (utils/restructure make-config-link-props method-id snapshot-id))]))]))
   :refresh
   (fn [{:keys [refs]}]
     (@refs "table" :refresh))})
