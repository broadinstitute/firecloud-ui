(ns broadfcui.page.method-repo.method.wdl
  (:require
   [dmohs.react :as react]
   [broadfcui.common.codemirror :refer [PipelineAndWDL]]
   [broadfcui.utils :as utils]
   ))

(react/defc WDLViewer
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:margin "2.5rem 1.5rem 1rem"}}
      [PipelineAndWDL {:wdl (:wdl props) :read-only? true}]])
   :refresh
   (constantly nil)})
