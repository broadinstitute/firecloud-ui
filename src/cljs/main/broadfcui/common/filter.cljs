(ns broadfcui.common.filter
  (:require
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))


(defn area [attributes & sections]
  [:div (utils/deep-merge {:style {:fontSize "85%" :padding "1rem"
                                   :background (:background-light style/colors)}}
                          attributes)
   (->> sections
        (remove nil?)
        (interpose [:hr {:style {:marginTop "0.9rem"}}]))])



(defn section [{:keys [title on-clear content]}]
  [:div {:data-test-id (str title "-facet_section")}
(defn section [{:keys [title on-clear content data-test-id]}]
  [:div {}
   (when (or title on-clear)
     (flex/box
      {:style {:marginBottom "0.5rem" :alignItems "baseline"}}
      (when title
        [:div {:data-test-id (str title "-title") :style {:fontWeight "bold"}} title])
      flex/spring
      (when on-clear
        [:div {:style {:fontSize "80%"}}
         (links/create-internal {:onClick on-clear :data-test-id (str (or data-test-id title) "-clear")} "Clear")])))
   content])


(defn checkboxes [{:keys [items checked-items on-change]}]
  (map (fn [{:keys [item render hit-count]}]
         (let [rendered ((or render identity) item)]
           [:div {:style {:display "flex" :paddingTop 5}}
            [:label {:style {:flex "1 1 auto"
                             :textOverflow "ellipsis" :overflow "hidden" :whiteSpace "nowrap"}
                     :title rendered
                     :data-test-id (str rendered "-item")}
             [:input {:type "checkbox"
                      :checked (contains? checked-items item)
                      :onChange #(on-change item (.. % -target -checked))}]
             [:span {:style {:marginLeft "0.25rem"}} rendered]]
            (some-> hit-count style/render-count)]))
       items))
