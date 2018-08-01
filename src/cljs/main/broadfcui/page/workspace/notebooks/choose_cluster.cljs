(ns broadfcui.page.workspace.notebooks.choose_cluster
  (:require
   [dmohs.react :as react]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.common.links :as links]
   [broadfcui.components.foundation-tooltip :refer [FoundationTooltip]]
   [broadfcui.page.workspace.notebooks.utils :as utils]
   ))

(react/defc ChooseClusterViewer
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [cluster-map choose-notebook]} props
           cluster-name (:cluster-name choose-notebook)
           cluster-menu-entry (fn [c] (str (:clusterName c) " (" (:status c) ")"))]
       [modals/OKCancelForm
        {:header "Choose Cluster"
         :dismiss (:dismiss-cluster-chooser props)
         :ok-button {:onClick #(this :-choose-cluster)}
         :content
         (react/create-element
          [:div {:style {:marginBottom -20}}
           (utils/create-inline-form-label (str "Choose a cluster to associate with notebook \"" (utils/notebook-name choose-notebook) "\":"))
           (style/create-identity-select {:data-test-id "cluster-select" :ref "clusterSelect"
                                          :style {:width "100%" :marginRight "4%" :marginTop 5}
                                          :default-value (when-not (nil? cluster-name) (cluster-menu-entry (get cluster-map cluster-name)))}
                                         (map cluster-menu-entry (vals cluster-map)))
           [:span {:style {:marginBottom "0.16667em" :fontSize "88%"}}
            (links/create-internal
             {:data-test-id "cluster-creator"
              :style {:textDecoration "none" :color (:button-primary style/colors)}
              :onClick #(this :-create-cluster)}
             "Or, create a new cluster.")]
           ])}]))

   :-choose-cluster
   (fn [{:keys [props refs]}]
     (let [selectedCluster (.-value (@refs "clusterSelect"))
           selectecClusterName (first (clojure.string/split selectedCluster #" "))]
       (do
         ((:choose-cluster props) selectecClusterName)
         ((:dismiss-cluster-chooser props)))))

   :-create-cluster
   (fn [{:keys [props refs]}]
     (do
       ((:create-cluster props))
       ((:dismiss-cluster-chooser props))))})