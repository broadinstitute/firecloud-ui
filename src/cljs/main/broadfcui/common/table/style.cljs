(ns broadfcui.common.table.style
  (:require
    [broadfcui.common.style :as style]
    ))


(def clip-text {:whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"})


(def table-light
  {:table {:fontSize "0.8rem"}
   :cell (merge clip-text {:padding "0.6rem 0 0.6rem 16px"})
   :header-row {:color (:text-light style/colors)}
   :body-row (constantly {:fontWeight 500 :borderTop style/standard-line})})

(def table-icon-size 16)