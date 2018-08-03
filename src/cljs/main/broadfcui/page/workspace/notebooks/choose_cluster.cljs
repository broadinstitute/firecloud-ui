(ns broadfcui.page.workspace.notebooks.choose_cluster
  (:require
   [dmohs.react :as react]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.common.style :as style]
   [broadfcui.common.links :as links]
   [broadfcui.page.workspace.notebooks.utils :as notebook-utils]
   ))

(react/defc ChooseClusterViewer
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [cluster-map choose-notebook]} props
           cluster-name (:cluster-name choose-notebook)
           cluster-menu-entry (fn [c] (str (:clusterName c) " (" (:status c) ")"))]
       [modals/OKCancelForm
        {:header "Choose Cluster"
         :dismiss (:dismiss props)
         :ok-button {:onClick #(this :-choose-cluster)}
         :content
         (react/create-element
          [:div {:style {:marginBottom -20}}
           (notebook-utils/create-inline-form-label (str "Choose a cluster to associate with notebook \""
                                                         (notebook-utils/notebook-name choose-notebook) "\":"))
           (style/create-identity-select {:data-test-id "cluster-select" :ref "clusterSelect"
                                          :style {:width "100%" :marginRight "4%" :marginTop 5}
                                          :default-value (when-not (nil? cluster-name) (cluster-menu-entry (get cluster-map cluster-name)))}
                                         (map cluster-menu-entry (vals cluster-map)))
           [:div {}
           [:span {:style {:marginBottom "0.16667em" :fontSize "88%"}}
            "Or, " (links/create-internal {:data-test-id "cluster-creator"
                                           :style {:textDecoration "none" :color (:button-primary style/colors)}
                                           :onClick #(this :-create-cluster)}
                                          "create a new cluster.")]]])}]))

   :-choose-cluster
   (fn [{:keys [props refs]}]
     (let [selectedCluster (.-value (@refs "clusterSelect"))
           selectedClusterName (first (clojure.string/split selectedCluster #" "))]
       ((:reload-after-choose props) selectedClusterName)
       ((:dismiss props))))

   :-create-cluster
   (fn [{:keys [props refs]}]
     ((:show-create-cluster props))
     ((:dismiss props)))})