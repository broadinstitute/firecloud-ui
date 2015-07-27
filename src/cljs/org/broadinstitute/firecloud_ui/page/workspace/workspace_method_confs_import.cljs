(ns org.broadinstitute.firecloud-ui.page.workspace.workspace-method-confs-import
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.page.workspace.workspace-method-confs :as methodconfs :refer [WorkspaceMethodsConfigurationsList  create-mock-methodconfs stringify_map ]       ]
    [org.broadinstitute.firecloud-ui.utils :as utils :refer [rlog cljslog jslog ] ]
    ))


(react/defc ImportWorkspaceMethodsConfigurationsList
  {:render
   (fn [{:keys [props refs state]}]
     [:div {}
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
           {:columns [{:header-component [:div {:style {:padding "13px 0 12px 12px"
                                                        :backgroundColor (:header-darkgray style/colors)}}
                                          [:input {:type "checkbox" :ref "allcheck"}]
                                          ]
                       :starting-width 42 :resizable? false
                       :cell-renderer (fn [row-num conf]
                                    [:div {:style {:paddingLeft 12}} [:input {:type "checkbox" :id (str "import_mc_checkbox_" row-num )
                                                                              :onClick (fn [e] (let [
                                                                                                   check_box_id_str (str "import_mc_checkbox_" row-num )
                                                                                                   clicked_checkbox_element (.getElementById js/document (name check_box_id_str))
                                                                                                   checked_status (.-checked clicked_checkbox_element)]
                                                                                               ))}]])}
                      {:header-component (header "Name") :starting-width 200
                       :cell-renderer (fn [row-num conf]
                                        (cell [:a {:href "javascript:;"
                                                   :style {:color (:button-blue style/colors)
                                                           :textDecoration "none"}} (conf "name")]))}
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
                       :cell-renderer (fn [row-num conf] (cell (stringify_map (conf "inputs"))))}
                      {:header-component (header "Outputs") :starting-width 200
                       :cell-renderer (fn [row-num conf] (cell (stringify_map (conf "outputs"))))}
                      {:header-component (header "Prerequisites") :starting-width 200
                       :cell-renderer (fn [row-num conf] (cell (stringify_map (conf "prerequisites"))))}]
            :data (:method-confs props)
            :row-props (fn [row-num conf]
                         {:style {:fontSize "80%" :fontWeight 500
                                  :paddingTop 10 :paddingBottom 7
                                  :backgroundColor (if (even? row-num) (:background-gray style/colors) "#fff")}})})])])})


(defn render-import-confs [workspace]
 [
   [:div {:align "right" :width "100%"}
     [comps/Button {:text "+ Add selected to workspace" :onClick (fn [] nil)}]
     [ImportWorkspaceMethodsConfigurationsList {:method-confs (utils/parse-json-string (utils/->json-string (create-mock-methodconfs)))}]]])

