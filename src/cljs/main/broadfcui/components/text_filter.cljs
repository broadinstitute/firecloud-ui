(ns broadfcui.components.text-filter
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   ))

(react/defc TextFilter
  {:set-text
   (fn [{:keys [refs]} text]
     (set! (.-value (@refs "filter-field")) text))
   :render
   (fn [{:keys [props this]}]
     (let [{:keys [initial-text placeholder width data-test-id]} props]
       [:div {:style {:display "inline-flex" :width width}}
        (style/create-search-field
         {:ref "filter-field" :autoSave "true" :results 5 :auto-focus "true"
          :data-test-id (str data-test-id "-input")
          :placeholder (or placeholder "Filter") :defaultValue initial-text
          :style {:flex "1 0 auto" :borderRadius "3px 0 0 3px" :marginBottom 0}
          :onKeyDown (common/create-key-handler [:enter] #(this :apply-filter))})
        [buttons/Button {:icon :search :onClick #(this :apply-filter)
                 :data-test-id (str data-test-id "-button")
                 :style {:flex "0 0 auto" :borderRadius "0 3px 3px 0"}}]]))
   :apply-filter
   (fn [{:keys [props refs]}]
     ((:on-filter props) (common/get-text refs "filter-field")))
   :component-did-mount
   (fn [{:keys [refs this]}]
     (.addEventListener (@refs "filter-field") "search"
                        #(when (and (seq @refs) (empty? (.. % -currentTarget -value)))
                           (this :apply-filter))))})


