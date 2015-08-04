(ns org.broadinstitute.firecloud-ui.page.workspace.workspace-method-confs
  (:require
    [dmohs.react :as react]
    [clojure.string :refer [join]]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.page.workspace.method-config-importer :as importmc]
    [org.broadinstitute.firecloud-ui.page.workspace.method-config-editor :refer [MethodConfigEditor]]
    [org.broadinstitute.firecloud-ui.utils :as utils]
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
      [:div {:style {:clear "both"}}]
      (if (zero? (count (:method-confs props)))
        [:div {:style {:textAlign "center" :backgroundColor (:background-gray style/colors)
                       :padding "1em 0" :margin "0 4em" :borderRadius 8}}
         "There are no method configurations to display."]
        [table/AdvancedTable
         (let [header (fn [children] [:div {:style {:fontWeight 600 :fontSize 13
                                                    :padding "14px 16px"
                                                    :borderLeft "1px solid #777777"
                                                    :color "#fff" :backgroundColor (:header-darkgray style/colors)}}
                                      children])
               cell (fn [children] [:span {:style {:paddingLeft 16}} children])]
           {:columns [{:header-component (header "Name") :starting-width 200
                       :cell-renderer (fn [row-num conf]
                                        (cell [:a {:href "javascript:;"
                                                   :style {:color (:button-blue style/colors)
                                                           :textDecoration "none"}
                                                   :onClick (fn [e]
                                                              (common/scroll-to-top)
                                                              (swap! (:parent-state props) assoc :selected-method-config conf))}
                                               (conf "name")]))}
                      {:header-component (header "Namespace") :starting-width 200
                       :cell-renderer (fn [row-num conf] (cell (conf "namespace")))}
                      {:header-component (header "Type") :starting-width 100
                       :cell-renderer (fn [row-num conf] (cell (conf "rootEntityType")))}
                      {:header-component (header "Workspace Name") :starting-width 160
                       :cell-renderer (fn [row-num conf] (cell (str ((conf "workspaceName") "namespace") ":"
                                                                 ((conf "workspaceName") "name"))))}
                      {:header-component (header "Method") :starting-width 210
                       :cell-renderer (fn [row-num conf] (cell (str ((conf "methodStoreMethod") "methodNamespace") ":"
                                                                 ((conf "methodStoreMethod") "methodName") ":"
                                                                 ((conf "methodStoreMethod") "methodVersion"))))}
                      {:header-component (header "Config") :starting-width 290
                       :cell-renderer (fn [row-num conf] (cell (str ((conf "methodStoreConfig") "methodConfigNamespace") ":"
                                                                 ((conf "methodStoreConfig") "methodConfigName") ":"
                                                                 ((conf "methodStoreConfig") "methodConfigVersion"))))}
                      {:header-component (header "Inputs") :starting-width 200
                       :cell-renderer (fn [row-num conf] (cell (utils/map-to-string (conf "inputs"))))}
                      {:header-component (header "Outputs") :starting-width 200
                       :cell-renderer (fn [row-num conf] (cell (utils/map-to-string (conf "outputs"))))}
                      {:header-component (header "Prerequisites") :starting-width 200
                       :cell-renderer (fn [row-num conf] (cell (join ", " (conf "prerequisites"))))}]
            :data (:method-confs props)
            :row-props (fn [row-num conf]
                         {:style {:fontSize "80%" :fontWeight 500
                                  :paddingTop 10 :paddingBottom 7
                                  :backgroundColor (if (even? row-num) (:background-gray style/colors) "#fff")}})})])])})


;; Rawls is screwed up right now: Prerequisites should simply be a list of strings, not a map.
;; Delete this when the backend is fixed
(defn- fix-configs [configs]
  (map (fn [conf] (assoc conf :prerequisites [])) configs))

(react/defc WorkspaceMethodConfigurations
  {:render
   (fn [{:keys [state props]}]
     [:div {:style {:padding "1em 0"}}
      [:div {}
       (cond
         (:selected-method-config @state) [MethodConfigEditor {:config (:selected-method-config @state)}]
         (:method-confs-loaded? @state) [WorkspaceMethodsConfigurationsList {:workspace (:workspace props)
                                                                             :method-confs (:method-confs @state)
                                                                             :parent-state state}]
         (:error-message @state) [:div {:style {:color "red"}}
                                  "FireCloud service returned error: " (:error-message @state)]
         :else [comps/Spinner {:text "Loading configurations..."}])]])
   :component-did-mount
   (fn [{:keys [state props]}]
     (utils/ajax-orch
       (str "/workspaces/" (get-in props [:workspace "namespace"]) "/" (get-in props [:workspace "name"]) "/methodconfigs")
       {:on-done (fn [{:keys [success? xhr]}]
                   (if success?
                     (swap! state assoc :method-confs-loaded? true :method-confs (fix-configs (utils/parse-json-string (.-responseText xhr))))
                     (swap! state assoc :error-message (.-statusText xhr))))
        :canned-response {:responseText (utils/->json-string (create-mock-methodconfs))
                          :status 200
                          :delay-ms (rand-int 2000)}}))
   :component-will-receive-props
   (fn [{:keys [state]}]
     (swap! state dissoc :selected-method-config))})

(defn render-workspace-method-confs [workspace]
  [WorkspaceMethodConfigurations {:workspace workspace}])
