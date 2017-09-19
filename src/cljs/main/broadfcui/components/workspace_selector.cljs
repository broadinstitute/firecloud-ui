(ns broadfcui.components.workspace-selector
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
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
           :ref (fn [elem]
                  ;; doing this manually because the ref handler thingy calls :did-mount overzealously
                  (when (and elem (not (:select @locals)))
                    (swap! locals assoc :select elem)
                    (.on (.select2 (js/$ elem)) "select2:select"
                         (fn [event]
                           (let [selected (nth workspaces (js/parseInt (.-value (.-target event))))]
                             (on-select selected))))
                    (on-select (first workspaces))))
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
                    (swap! state assoc :error status-text)))}))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.select2 (js/$ (:select @locals)) "destroy"))})
