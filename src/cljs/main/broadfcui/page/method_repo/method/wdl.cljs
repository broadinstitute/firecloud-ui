(ns broadfcui.page.method-repo.method.wdl
  (:require
   [dmohs.react :as react]
   [broadfcui.common.codemirror :refer [CodeMirror]]
   [broadfcui.utils :as utils]
   ))

(react/defc WDLViewer
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:marginTop "2.5rem" :marginLeft "1.5rem" :marginRight "1.5rem" :marginBottom "1.0rem"}}
      [CodeMirror {:text (:wdl props)}]])
   :refresh
   (constantly nil)})
