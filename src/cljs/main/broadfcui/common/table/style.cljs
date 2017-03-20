(ns broadfcui.common.table.style
  (:require
    [broadfcui.common.style :as style]
    ))


(def billing-management-style
  {:header-row {:fontSize "80%" :color (:text-light style/colors)}
   :header-cell {:whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"
                 :padding "0.8em 0 0.8em 16px"}
   :body-row (constantly {:fontSize "80%" :fontWeight 500
                          :border-top style/standard-line :padding "0.2rem 0"})
   :body-cell {:whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"
               :padding "0.6em 0 0.6em 16px"}})
