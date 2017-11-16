(ns broadfcui.page.workspace.notebooks.tab
  (:require
   [dmohs.react :as react]
   [broadfcui.page.workspace.notebooks.notebooks :refer [NotebooksContainer]]
   [broadfcui.utils :as utils]
   ))


(react/defc Page
  {:refresh
   (fn [{:keys [refs]}]
     ((@refs "container") :refresh))
   :render
   (fn [{:keys [props]}]
     [NotebooksContainer (assoc props :ref "container")])})
