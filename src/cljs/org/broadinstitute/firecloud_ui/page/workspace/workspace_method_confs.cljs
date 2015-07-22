(ns org.broadinstitute.firecloud-ui.page.workspace.workspace-method-confs
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.style :as style]))


(defn- create-mock-methodconfs []
  (map
    (fn [i]
      {:method-conf-name (rand-nth ["rand_name_1" "rand_name_2" "rand_name_3" "rand_name_4"])
       :method-conf-root-ent-type (str "method_conf_root_ent_type_" (inc i))
       :method-conf-last-updated (str "method_conf_last_updated_" (inc i))})
    (range (rand-int 10))))


(react/defc WorkspaceMethodsConfigurationsList
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:padding "0 4em"}}
      (if (zero? (count (:method-confs props)))
        [:div {:style {:textAlign "center" :backgroundColor (:background-gray style/colors)
                       :padding "1em 0" :borderRadius 8}}
         "There are no method configurations to display."]
        [table/Table
         (let [cell-style {:flexBasis  "8ex" :flexGrow 1 :whiteSpace "nowrap" :overflow "hidden"
                           :borderLeft (str "1px solid " (:line-gray style/colors))}
               header-label (fn [text & [padding]]
                              [:span {:style {:paddingLeft (or padding "1em")}}
                               [:span {:style {:fontSize "90%"}} text]])]
           {:columns [{:label (header-label "Conf Name")
                       :style (merge cell-style {:borderLeft "none"})}
                      {:label (header-label "Conf Root Entity Type")
                       :style cell-style
                       :header-style {:borderLeft "none"}}
                      {:label (header-label "Last Updated")
                       :style (merge cell-style {:flexBasis "30ex"})
                       :header-style {:borderLeft "none"}}]
            :data (map (fn [m]
                         [(m "method-conf-name")
                          (m "method-conf-root-ent-type")
                          (m "method-conf-last-updated")])
                    (:method-confs props))})])])})


(react/defc WorkspaceMethodConfigurations
  {:component-did-mount
   (fn [{:keys [state props]}]
     (utils/ajax-orch
       (str "/" (:selected-workspace-namespace props) "/" (:selected-workspace props) "/methodconfigs")
       {:on-done (fn [{:keys [success? xhr]}]
                   (if success?
                     (swap! state assoc :method-confs-loaded? true :method-confs (utils/parse-json-string (.-responseText xhr)))
                     (swap! state assoc :error-message (.-statusText xhr))))
        :canned-response {:responseText (utils/->json-string (create-mock-methodconfs))
                          :status 200
                          :delay-ms (rand-int 2000)}}))
   :render
   (fn [{:keys [state]}]
     [:div {:style {:padding "1em"}}
      [:div {}
       (cond
         (:method-confs-loaded? @state) [WorkspaceMethodsConfigurationsList {:method-confs (:method-confs @state)}]
         (:error-message @state) [:div {:style {:color "red"}}
                                  "FireCloud service returned error: " (:error-message @state)]
         :else [comps/Spinner {:text "Loading configurations..."}])]])})

(defn render-workspace-method-confs [workspace]
  [WorkspaceMethodConfigurations
   {:selected-workspace (workspace "name")
    :selected-workspace-namespace (workspace "namespace")}])
