(ns broadfcui.common.method.messages
  (:require
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))


(defn get-methods-repo-group-alert []
  [:div {:style {:paddingBottom "1em" :fontSize "100%"}}
   (icons/icon {:style {:fontSize 22 :color (:exception-state style/colors) :marginRight "1rem"}}
               :warning)
   (str "Currently user groups are not supported in the Method Repository. "
        "That is, you cannot share a method/configuration with a user group. "
        "We appreciate your patience as we work on developing this feature.")])