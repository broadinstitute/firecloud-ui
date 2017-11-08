(ns broadfcui.page.workspace.notebooks.tab
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.modal :as modal]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.page.workspace.notebooks.notebooks :refer [NotebooksContainer]]
   [broadfcui.utils :as utils]
   ))


(def ^:private tracks-cache (atom {}))

(react/defc Page
  {:refresh
   (fn [])
   :get-initial-state
   (fn [{:keys [props]}]
     {:tracks (get @tracks-cache (:workspace-id props) [])})
   :render
   (fn [{:keys [state this props]}]
     (utils/log props)
     (if true ;; if the user is in the whitelist
       [:div {}
        [NotebooksContainer props]
        (common/clear-both)]))
   :component-will-unmount
   (fn [{:keys [props state]}]
     (swap! tracks-cache assoc (:workspace-id props) (:tracks @state)))})