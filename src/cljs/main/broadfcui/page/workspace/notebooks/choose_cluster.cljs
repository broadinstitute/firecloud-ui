(ns broadfcui.page.workspace.notebooks.choose-cluster
  (:require
   [dmohs.react :as react]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.modals :as modals]
   [broadfcui.page.workspace.notebooks.utils :as notebook-utils]
   ))

(react/defc ChooseClusterViewer
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [cluster-map choose-notebook dismiss]} props
           cluster-name (:cluster-name choose-notebook)
           cluster-menu-entry (fn [{:keys [clusterName status]}] (str clusterName " (" status ")"))]
       [modals/OKCancelForm
        {:header "Choose Cluster"
         :dismiss dismiss
         :ok-button {:onClick #(this :-choose-cluster)}
         :content
         (react/create-element
          [:div {:style {:marginBottom -20}}
           (notebook-utils/create-inline-form-label
            (str "Choose a cluster to associate with notebook \""
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
           selectedClusterName (first (clojure.string/split selectedCluster #" "))
           {:keys [reload-after-choose dismiss]} props]
       (reload-after-choose selectedClusterName)
       (dismiss)))

   :-create-cluster
   (fn [{:keys [props refs]}]
     (let [{:keys [show-create-cluster dismiss]} props]
       (show-create-cluster)
       (dismiss)))})
