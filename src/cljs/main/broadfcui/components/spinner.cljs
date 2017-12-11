(ns broadfcui.components.spinner
  (:require
   [clojure.string :as string]
   [broadfcui.common.icons :as icons]
   [broadfcui.utils :as utils]
   ))


(defn spinner
  ([]
   (spinner {} ""))
  ([attrs-or-text]
   (if (map? attrs-or-text)
     (spinner attrs-or-text "")
     (spinner {} attrs-or-text)))
  ([attributes text]
   [:span (utils/deep-merge {:data-test-id "spinner"
                             :style {:margin "1em" :whiteSpace "nowrap" :display "inline-block"}}
                            attributes)
    (icons/render-icon {:className "fa-pulse fa-fw"
                        :style {:marginRight (when-not (string/blank? text) "0.5rem")}}
                       :spinner)
    [:span {:data-test-id "spinner-text"} text]]))
