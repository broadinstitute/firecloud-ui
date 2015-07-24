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
      {:name (rand-nth ["rand_name_1" "rand_name_2" "rand_name_3" "rand_name_4"])
       :namespace (str "ns_s_" (inc i))
       :root-ent-type (str "r_e_t_" (inc i))
       :workspaceName {:namespace (str "ws_ns_" (inc i))
                       :name (str "ws_n_" (inc i))}
       :methodStoreMethod {:methodNamespace (str "ms_ns_" (inc i))
                           :methodName (str "ms_n_" (inc i))
                           :methodVersion (str "ms_v_" (inc i))}
       :methodStoreConfig {:namespace (str "msc_ns_" (inc i))
                           :name (str "msc_n_" (inc i))
                           :version (str "msc_v_" (inc i))}
       :inputs {:i1 (str "i_1_" (inc i))
                :i2 (str "i_2_" (inc i))}
       :outputs {:o1 (str "o_1_" (inc i))
                 :o2 (str "o_2_" (inc i))}
       :prerequisites {:p1 (str "p_1_" (inc i))
                       :p2 (str "p_2_" (inc i))}})
    (range (rand-int 50))))


(defn stringify_map [the_map]
  (for [k (keys the_map)]
    (str k "," (get the_map k) " ; ")))


(react/defc WorkspaceMethodsConfigurationsList
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:padding "0 4em"}}
      (if (zero? (count (:method-confs props)))
        [:div {:style {:textAlign "center" :backgroundColor (:background-gray style/colors)
                       :padding "1em 0" :borderRadius 8}}
         "There are no method configurations to display."]
        [table/Table
         (let [cell-style {:flexBasis "8ex" :flexGrow 1 :whiteSpace "nowrap" :overflow "hidden"
                           :borderLeft (str "1px solid " (:line-gray style/colors))}
               header-label (fn [text & [padding]]
                              [:span {:style {:paddingLeft (or padding "1em")}}
                               [:span {:style {:fontSize "90%"}} text]])]
           {:columns [{:label (header-label "Name")
                       :style (merge cell-style {:borderLeft "none"})}
                      {:label (header-label "Namespace")
                       :style cell-style
                       :header-style {:borderLeft "none"}}
                      {:label (header-label "Root Entity Type")
                       :style cell-style
                       :header-style {:borderLeft "none"}}
                      {:label (header-label "Workspace Name")
                       :header-style {:borderLeft "none"}
                       :style cell-style}
                      {:label (header-label "Method Store Method")
                       :style cell-style
                       :header-style {:borderLeft "none"}}
                      {:label (header-label "Method Store Config")
                       :style cell-style
                       :header-style {:borderLeft "none"}}
                      {:label (header-label "Inputs")
                       :header-style {:borderLeft "none"}
                       :style cell-style}
                      {:label (header-label "Outputs")
                       :style cell-style
                       :header-style {:borderLeft "none"}}
                      {:label (header-label "Pre-Requisites")
                       :style (merge cell-style {:flexBasis "30ex"})
                       :header-style {:borderLeft "none"}}]
            :data    (map (fn [m]
                            [(m "name")
                             (m "namespace")
                             (m "root-ent-type")
                             (str ((m "workspaceName") "namespace") ":"
                               ((m "workspaceName") "name"))
                             (str ((m "methodStoreMethod") "methodNamespace") ":"
                               ((m "methodStoreMethod") "methodName") ":"
                               ((m "methodStoreMethod") "methodVersion"))
                             (str (get (m "methodStoreConfig") "namespace") ":"
                               ((m "methodStoreConfig") "name") ":"
                               ((m "methodStoreConfig") "version"))
                             (stringify_map (m "inputs"))
                             (stringify_map (m "outputs"))
                             (stringify_map (m "prerequisites"))])
                          (:method-confs props))})])])})


(react/defc WorkspaceMethodConfigurations
  {:component-did-mount
   (fn [{:keys [state props]}]
     (utils/ajax-orch
       (str "/workspaces/" (:selected-workspace-namespace props) "/" (:selected-workspace props) "/methodconfigs")
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



