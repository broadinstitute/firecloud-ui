(ns broadfcui.components.buttons
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   ))


(defonce modal-constructor (atom nil))


(defn- show-message [disabled? on-dismiss]
  (cond (true? disabled?)
        (@modal-constructor :message {:header "Disabled" :text "This action is disabled" :on-dismiss on-dismiss})
        (string? disabled?)
        (@modal-constructor :message {:header "Disabled" :text disabled? :on-dismiss on-dismiss})
        (map? disabled?)
        (@modal-constructor
         (if (= (:type disabled?) :error) :error :message)
         (assoc disabled? :on-dismiss on-dismiss))))


(defn- make-default-test-id [{:keys [text icon]}]
  (if text
    (-> text
        string/lower-case
        (string/replace-all #"\s+" "-")
        (string/replace-all #"[^a-z\-]" "")
        (str "-button"))
    (str (name icon) "-button")))


(react/defc Button
  {:get-default-props
   (fn []
     {:type :primary
      :color (:button-primary style/colors)})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [type color icon href disabled? onClick text style class-name data-test-id]} props
           color (if disabled? (:disabled-state style/colors) color)]
       (assert (or text icon) "Button must have text and/or icon")
       [:a {:data-test-id (or data-test-id (make-default-test-id props))
            :data-test-state (if disabled? "disabled" "enabled")
            :className (or class-name "button")
            :style (merge
                    (case type
                      :primary {:backgroundColor color :color "white"}
                      :secondary {:backgroundColor "white" :color color
                                  :border (str "1px solid " color)})
                    {:display "inline-flex" :alignItems "center" :justifyContent "center"
                     :flexShrink 0
                     :cursor (when disabled? "default")
                     :fontWeight 500
                     :minHeight 19 :minWidth 19
                     :borderRadius 2 :padding (if text "0.7em 1em" "0.4em")
                     :textDecoration "none"}
                    (if (map? style) style {}))
            :href (or href "javascript:;")
            :onClick (if disabled? #(swap! state assoc :show-message? true) onClick)
            :onKeyDown (when (and onClick (not disabled?))
                         (common/create-key-handler [:space] onClick))}
        (when (:show-message? @state)
          (show-message disabled? #(swap! state dissoc :show-message?)))
        text
        (some->> icon (icons/icon {:style (if text
                                            {:fontSize 20 :margin "-0.5em -0.3em -0.5em 0.5em"}
                                            {:fontSize 18})}))]))})


(react/defc SidebarButton
  {:get-default-props
   (fn []
     {:style :heavy})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [text icon onClick style disabled? margin color data-test-id]} props
           heavy? (= :heavy style)
           color (if (keyword? color) (get style/colors color) color)]
       (assert (and text icon) "Button must have text and icon")
       [:div {:data-test-id (or data-test-id (make-default-test-id props))
              :data-test-state (if disabled? "disabled" "enabled")
              :style {:display "flex" :flexWrap "nowrap" :alignItems "center"
                      :marginTop (when (= margin :top) "1rem")
                      :marginBottom (when (= margin :bottom) "1rem")
                      :border (when-not heavy? style/standard-line)
                      :borderRadius 5
                      :padding "0.75rem 0"
                      :cursor (if disabled? "default" "pointer")
                      :backgroundColor (if disabled? (:disabled-state style/colors) (if heavy? color "transparent"))
                      :color (if heavy? "#fff" color)
                      :fontSize "106%"}
              :onClick (if disabled?
                         #(swap! state assoc :show-message? true)
                         onClick)}
        (when (:show-message? @state)
          (show-message disabled? #(swap! state dissoc :show-message?)))
        (icons/icon {:style {:padding "0 20px" :borderRight style/standard-line} :className "fa-fw"} icon)
        [:div {:style {:textAlign "center" :margin "auto"}}
         text]]))})


(defn- x-button [dismiss]
  [:div {:style {:float "right" :marginRight "-28px" :marginTop "-1px"}}
   [:a {:data-test-id "x-button"
        :style {:color (:text-light style/colors)}
        :href "javascript:;"
        :onClick dismiss}
    (icons/icon {:style {:fontSize "80%"}} :close)]])
