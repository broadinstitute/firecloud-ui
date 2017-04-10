(ns broadfcui.common.filter
  (:require
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))


(defn area [attributes & sections]
  [:div (utils/deep-merge {:style {:fontSize "85%" :padding "16px 12px"
                                   :background (:background-light style/colors)
                                   :border style/standard-line}}
                          attributes)
   (interpose [:hr] sections)])

(defn section [{:keys [title on-clear content]}]
  [:div {:style {:paddingBottom "0.9rem"}}
   (when title
     [:span {:style {:fontWeight "bold"}} title])
   (when on-clear
     [:div {:style {:fontSize "80%" :float "right"}}
      (style/create-link {:text "Clear" :onClick on-clear})])
   [:div {:style {:paddingTop "1em"}}
    content]])

(defn checkboxes [{:keys [items checked-items on-change]}]
  (map
   (fn [{:keys [item render hit-count]}]
     (let [rendered (render item)]
       [:div {:style {:paddingTop 5}}
        [:label {:style {:display "inline-block" :width "calc(100% - 30px)"
                         :textOverflow "ellipsis" :overflow "hidden" :whiteSpace "nowrap"}
                 :title rendered}
         [:input {:type "checkbox"
                  :checked (contains? checked-items item)
                  :onChange #(on-change item (.. % -target -checked))}]
         [:span {:style {:marginLeft "0.25rem"}} rendered]]
        (some-> hit-count style/render-count)]))
   items))
