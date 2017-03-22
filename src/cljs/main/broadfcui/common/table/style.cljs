(ns broadfcui.common.table.style
  (:require
    [broadfcui.common.style :as style]
    ))


(def table-icon-size 16)

(def clip-text {:whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"})

(def table-light
  {:table {:fontSize "0.8rem"}
   :cell (merge clip-text {:padding "0.6rem 0 0.6rem 16px"})
   :header-row {:color (:text-light style/colors)}
   :body {:fontWeight 500}
   :body-row (constantly {:borderTop style/standard-line})
   :resize-tab {:borderRight style/standard-line :marginRight -1}})
