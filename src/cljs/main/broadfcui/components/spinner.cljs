(ns broadfcui.components.spinner
  (:require
   [broadfcui.common.icons :as icons]
   [broadfcui.utils :as utils]))


(defn spinner
  ([]
   (spinner {} ""))
  ([text]
   (spinner {} text))
  ([attributes text]
   [:span (utils/deep-merge {:data-test-id "spinner"
                             :style {:margin "1em" :whiteSpace "nowrap" :display "inline-block"}}
                            attributes)
    (icons/render-icon {:className "fa-pulse fa-lg fa-fw" :style {:marginRight "0.5rem"}} :spinner)
                            [:span {:data-test-id "spinner-text"} text]]))
