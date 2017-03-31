(ns broadfcui.common.table-style
  (:require [broadfcui.common.style :as style]))

(def header-row-style-light
  {:fontWeight nil
   :color (:text-light style/colors) :backgroundColor nil})

(def table-row-style-light
  {:borderTop style/standard-line
   :padding "0.2rem 0"})

(def table-cell-plank-left
  {:borderRadius "8px 0 0 8px"
   :margin "4px 0" :marginRight -20
   :padding "0.6rem" :paddingRight "calc(0.6rem + 20px)"
   :backgroundColor (:background-light style/colors)})

(def table-cell-plank-right
  {:borderRadius "0 8px 8px 0"
   :margin "4px 0" :padding "0.6rem"
   :backgroundColor (:background-light style/colors)})
