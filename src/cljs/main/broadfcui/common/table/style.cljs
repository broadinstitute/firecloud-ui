(ns broadfcui.common.table.style
  (:require
    [broadfcui.common.style :as style]
    ))


(def clip-text {:whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"})


(def billing-management-style
  {:table {:fontSize "80%"}
   :cell (merge clip-text {:padding "0.8em 0 0.8em 16px"})
   :header-row {:color (:text-light style/colors)}
   :body-row (constantly {:fontWeight 500 :borderTop style/standard-line})})
