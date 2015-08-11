(ns org.broadinstitute.firecloud-ui.page.workspace.workspace-method-confs
  (:require
    [dmohs.react :as react]
    clojure.string
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.page.workspace.method-config-importer :as importmc]
    [org.broadinstitute.firecloud-ui.page.workspace.method-config-editor :refer [MethodConfigEditor]]
    [org.broadinstitute.firecloud-ui.paths :refer [list-method-configs-path]]
    [org.broadinstitute.firecloud-ui.utils :as utils :refer [rlog jslog cljslog]]
    ))


(defn- create-mock-methodconfs []
  (map
    (fn [i]
      {:name (str "Configuration " (inc i))
       :namespace (rand-nth ["Broad" "nci" "public"])
       :rootEntityType "Task"
       :workspaceName {:namespace (str "ws_ns_" (inc i))
                       :name (str "ws_n_" (inc i))}
       :methodStoreMethod {:methodNamespace (str "ms_ns_" (inc i))
                           :methodName (str "ms_n_" (inc i))
                           :methodVersion (str "ms_v_" (inc i))}
       :methodStoreConfig {:methodConfigNamespace (str "msc_ns_" (inc i))
                           :methodConfigName (str "msc_n_" (inc i))
                           :methodConfigVersion (str "msc_v_" (inc i))}
       :inputs {"Input 1" "[some value]"
                "Input 2" "[some value]"}
       :outputs {"Output 1" "[some value]"
                 "Output 2" "[some value]"}
       :prerequisites ["Predicate 1" "Predicate 2"]})
    (range (rand-int 50))))


(react/defc WorkspaceMethodsConfigurationsList
  {:render
   (fn [{:keys [props refs state]}]
     [:div {}
      (importmc/render-import-overlay state (:workspace props) )
      [:div {:style {:float "right" :padding "0 2em 1em 0"}}
       [comps/Button {:text "Import Configurations..."
                      :onClick #(swap! state assoc :import-overlay-shown? true)}]]
      (common/clear-both)
      (if (zero? (count (:method-confs props)))
        [:div {:style {:textAlign "center" :backgroundColor (:background-gray style/colors)
                       :padding "1em 0" :margin "0 4em" :borderRadius 8}}
         "There are no method configurations to display."]
        [table/Table
         {:columns
          [{:header "Name" :starting-width 200 :sort-by #(% "name")
            :content-renderer
            (fn [row-index config]
              [:a {:href "javascript:;"
                   :style {:color (:button-blue style/colors) :textDecoration "none"}
                   :onClick (fn [e]
                              (common/scroll-to-top)
                              (swap! (:parent-state props) assoc :selected-method-config config :selected-index row-index))}
               (config "name")])}
           {:header "Namespace" :starting-width 200 :sort-by :value}
           {:header "Type" :starting-width 100 :sort-by :value}
           {:header "Workspace Name" :starting-width 160}
           {:header "Method" :starting-width 210}
           {:header "Config" :starting-width 290}
           {:header "Inputs" :starting-width 200}
           {:header "Outputs" :starting-width 200}
           {:header "Prerequisites" :starting-width 200}]
          :data (map
                  (fn [config]
                    [config
                     (config "namespace")
                     (config "rootEntityType")
                     (clojure.string/join
                       ":" (map #(get-in config ["workspaceName" %]) ["namespace" "name"]))
                     (clojure.string/join
                       ":" (map #(get-in config ["methodStoreMethod" %])
                             ["methodNamespace" "methodName" "methodVersion"]))
                     (clojure.string/join
                       ":" (map #(get-in config ["methodStoreConfig" %])
                             ["methodConfigNamespace" "methodConfigName" "methodConfigVersion"]))
                     (utils/map-to-string (config "inputs"))
                     (utils/map-to-string (config "outputs"))
                     (clojure.string/join ", " (config "prerequisites"))])
                  (:method-confs props))}])])})


;; Rawls is screwed up right now: Prerequisites should simply be a list of strings, not a map.
;; Delete this when the backend is fixed
(defn- fix-configs [configs]
  (if utils/use-live-data?
    (mapv (fn [conf] (assoc conf "prerequisites" (vals (conf "prerequisites")))) configs)
    configs))

(defn- load-workspaces [state props]
  (swap! state assoc :method-confs-loaded? false)
  (utils/ajax-orch
    (list-method-configs-path (:workspace props))
    {:on-done (fn [{:keys [success? xhr]}]
                (if success?
                  (swap! state assoc :method-confs-loaded? true
                    :method-confs (fix-configs (vec (utils/parse-json-string (.-responseText xhr)))))
                  (swap! state assoc :error-message (.-statusText xhr))))
     :canned-response {:responseText (utils/->json-string (create-mock-methodconfs))
                       :status 200
                       :delay-ms (rand-int 2000)}}))

(react/defc WorkspaceMethodConfigurations
  {:render
   (fn [{:keys [state props]}]
     [:div {:style {:padding "1em 0"}}
      [:div {}
       (cond
         (:selected-method-config @state)
         [MethodConfigEditor {:workspace (:workspace props)
                              :config (:selected-method-config @state)
                              :onCommit (fn [new-conf]
                                          (if utils/use-live-data?
                                            (load-workspaces state props)
                                            (swap! state update-in [:method-confs]
                                              assoc (:selected-index @state) new-conf)))}]
         (:method-confs-loaded? @state) [WorkspaceMethodsConfigurationsList {:workspace (:workspace props)
                                                                             :method-confs (:method-confs @state)
                                                                             :parent-state state}]
         (:error-message @state) [:div {:style {:color "red"}}
                                  "FireCloud service returned error: " (:error-message @state)]
         :else [comps/Spinner {:text "Loading configurations..."}])]])
   :component-did-mount
   (fn [{:keys [state props]}]
     (load-workspaces state props))
   :component-will-receive-props
   (fn [{:keys [state]}]
     (swap! state dissoc :selected-method-config :selected-index))})

(defn render-workspace-method-confs [workspace]
  [WorkspaceMethodConfigurations {:workspace workspace}])
