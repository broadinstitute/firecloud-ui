(ns broadfcui.page.method-repo.method.wdl
  (:require
   [dmohs.react :as react]
   [broadfcui.common.codemirror :refer [CodeMirror]]
   [broadfcui.utils :as utils]
   ))

(react/defc WDLViewer
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:margin "2.5rem 1.5rem"}}
      [CodeMirror {:text (:wdl props)}]])
   :refresh
   (constantly nil)})
