(ns broadfcui.common.table.style
  (:require
   [broadfcui.common.style :as style]
   ))


(def table-icon-size 16)

(def clip-text {:whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"})

(def default-cell-left {:paddingLeft "16px"})

(defn tab [color]
  {:borderRight (str "1px solid " (if (keyword? color) (color style/colors) color))})

(def table-light
  {:table {:fontSize "0.8rem"}
   :cell (merge clip-text default-cell-left {:paddingTop "0.6rem" :paddingBottom "0.6rem"})
   :header-row {:color (:text-light style/colors)}
   :body {:fontWeight 500}
   :body-row (constantly {:borderTop style/standard-line :alignItems "baseline"})
   :resize-tab (tab :line-default)})

(def table-heavy
  {:table {:fontSize "0.8rem" :fontWeight 500}
   :cell (merge clip-text default-cell-left)
   :header-row {:backgroundColor (:background-dark style/colors) :color "white"}
   :header-cell {:paddingTop "0.6rem" :paddingBottom "0.6rem"}
   :resize-tab (tab "#777")
   :body-row (fn [{:keys [index]}]
               {:backgroundColor (if (odd? index) "white" (:background-light style/colors))})
   :body-cell {:paddingTop "0.5rem" :paddingBottom "0.4rem"}})

(def table-cell-plank-left
  {:borderRadius "8px 0 0 8px"
   :padding "0.6rem 0 0.6rem 16px"
   :backgroundColor (:background-light style/colors)})

(def table-cell-plank-middle
  {:padding "0.6rem 0 0.6rem 16px"
   :backgroundColor (:background-light style/colors)})

(def table-cell-plank-right
  {:borderRadius "0 8px 8px 0"
   :padding "0.6rem 16px"
   :backgroundColor (:background-light style/colors)})

(def table-cell-optional
  {:fontStyle "italic"
   :fontWeight 400})
