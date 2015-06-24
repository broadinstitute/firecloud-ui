(ns org.broadinstitute.firecloud-ui.common.table
  (:require
   [clojure.string]
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.utils :as utils :refer [rlog jslog cljslog]]
   ))


(react/defc DefaultCell
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :padding "1em"}} (:data props)])})


(react/defc CellContainer
  {:render
   (fn [{:keys [props]}]
     [:div {:style (:style props)}
      (let [CellComponent (or (:component props) DefaultCell)]
        [CellComponent {:data (:data props) :index (:index props)}])])})


(react/defc Body
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:border (str "1px solid " (:line-gray common/colors))
                    :borderRadius 4}}
      (map-indexed (fn [i row]
                     [:div {:style {:display "flex" :height "3.4em"
                                    :borderTop (when-not (zero? i)
                                                 (str "1px solid " (:line-gray common/colors)))}}
                      (map-indexed
                       (fn [i cell-data]
                         [CellContainer {:index i
                                         :component (get-in props [:columns i :component])
                                         :style (get-in props [:columns i :style])
                                         :data cell-data}])
                       row)])
                   (:data props))])})


(react/defc Table
  {:render
   (fn [{:keys [props]}]
     [:div {}
      [:div {:style {:color (:text-light common/colors)
                     :paddingBottom "1em" :display "flex"}}
       (map (fn [column]
              [:div {:style (merge (:style column) (:header-style column))}
               (:label column)])
            (:columns props))]
      [Body {:columns (:columns props) :data (:data props)}]])})
