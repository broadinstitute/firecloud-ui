(ns broadfcui.common.method.messages
  (:require
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))


(def methods-repo-group-alert
  [:p {:style {:fontSize "100%"}}
   (icons/icon {:style {:fontSize 22 :color (:exception-state style/colors) :marginRight "1rem"}}
               :warning)
   "Note: Sharing with user-groups is not yet supported."])
