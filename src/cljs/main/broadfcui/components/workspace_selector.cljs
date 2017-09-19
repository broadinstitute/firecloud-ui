(ns broadfcui.components.workspace-selector
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(react/defc WorkspaceSelector
  {:get-default-props
   (fn []
     {:filter identity
      :sort-by (comp (partial mapv string/lower-case)
                     (juxt :namespace :name)
                     :workspace)})
   :render
   (fn [{:keys [props state locals]}]
     (let [{:keys [workspaces]} @state
           {:keys [on-select style]} props]
       (assert on-select ":on-select is required for WorkspaceSelector")
       (if-not workspaces
         [comps/Spinner {:text "Loading workspaces..."}]
         (style/create-select
          {:defaultValue ""
           :ref (common/create-element-ref-handler
                 {:store locals
                  :element-key :workspace-select
                  :did-mount
                  #(.on (.select2 (js/$ %)) "select2:select"
                        (fn [event]
                          (let [selected (nth workspaces (js/parseInt (.-value (.-target event))))]
                            (on-select selected))))
                  :will-unmount
                  #(.select2 (js/$ %) "destroy")})
           :style (merge {:width 500} style)}
          (map (fn [ws] (string/join "/" (replace (:workspace ws) [:namespace :name])))
               workspaces)))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint endpoints/list-workspaces
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (swap! state assoc :workspaces (->> (get-parsed-response)
                                                        (filter (:filter props))
                                                        (sort-by (:sort-by props))))
                    (swap! state assoc :error status-text)))}))})
