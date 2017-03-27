(ns broadfcui.common.table.style
  (:require
    [broadfcui.common.style :as style]
    ))


(def table-icon-size 16)

(def clip-text {:whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"})

(defn tab [color]
  {:borderRight (str "1px solid " (if (keyword? color) (color style/colors) color))
   :marginRight -1})

(def table-light
  {:table {:fontSize "0.8rem"}
   :cell (merge clip-text {:padding "0.6rem 0 0.6rem 16px"})
   :header-row {:color (:text-light style/colors)}
   :body {:fontWeight 500}
   :body-row (constantly {:borderTop style/standard-line})
   :resize-tab {:borderRight style/standard-line :marginRight -1}})

(def table-heavy
  {:table {:fontSize "0.8rem" :fontWeight 500}
   :cell clip-text
   :header-row {:backgroundColor (:background-dark style/colors) :color "white"}
   :header-cell {:padding "0.6rem 0 0.6rem 16px"}
   :resize-tab (tab "#777")
   :body-row (fn [{:keys [index]}]
               {:backgroundColor (if (odd? index) "white" (:background-light style/colors))})
   :body-cell {:padding "0.5rem 0 0.4rem 16px"}})
