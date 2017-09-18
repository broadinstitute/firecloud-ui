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
     (let [{:keys [workspaces-list]} @state
           {:keys [on-select]} props]
       (assert on-select ":on-select is required for WorkspaceSelector")
       (if-not workspaces-list
         [comps/Spinner {:text "Loading workspaces..."}]
         (style/create-select
          {:defaultValue ""
           :ref (common/create-element-ref-handler
                 {:store locals
                  :element-key :workspace-select
                  :did-mount
                  #(.on (.select2 (js/$ %)) "select2:select"
                        (fn [event]
                          (let [selected (nth workspaces-list (js/parseInt (.-value (.-target event))))]
                            (on-select selected))))
                  :will-unmount
                  #(.select2 (js/$ %) "destroy")})
           :style {:width 500}}
          (map (fn [ws] (clojure.string/join "/" (replace (:workspace ws) [:namespace :name])))
               workspaces-list)))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint endpoints/list-workspaces
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (let [ws-list (->> (get-parsed-response)
                                       (filter (:filter props))
                                       (sort-by (:sort-by props)))]
                      (swap! state assoc :workspaces-list ws-list :selected-workspace (first ws-list)))
                    (swap! state assoc :error status-text)))}))})
