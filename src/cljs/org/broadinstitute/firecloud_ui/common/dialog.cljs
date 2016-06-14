(ns org.broadinstitute.firecloud-ui.common.dialog
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :refer [Spinner Button]]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(react/defc Dialog
  {:get-default-props
   (fn []
     {:blocking? true
      :cycle-focus? false})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [content width blocking? dismiss-self get-anchor-dom-node]} props
           anchored? (not (nil? get-anchor-dom-node))]
       (assert (react/valid-element? content)
         (subs (str "Not a react element: " content) 0 200))
       (when (or (not anchored?) (:position @state))
         [:div {:style {:backgroundColor (if blocking?
                                           "rgba(110, 110, 110, 0.4)"
                                           "rgba(210, 210, 210, 0.4)")
                        :position "absolute" :zIndex 8888
                        :top 0 :left 0 :right 0 :height (.. js/document -body -offsetHeight)}
                :onKeyDown (common/create-key-handler [:esc] dismiss-self)
                :onClick (when-not blocking? dismiss-self)}
          [:div {:style (if anchored?
                          {:position "absolute" :backgroundColor "#fff"
                           :top (get-in @state [:position :top])
                           :left (get-in @state [:position :left])}
                          {:transform "translate(-50%, 0px)" :backgroundColor "#fff"
                           :position "relative" :marginBottom 60
                           :top 60 :left "50%" :width width})
                 :onClick (when-not blocking? #(.stopPropagation %))}
           content]])))
   :component-did-mount
   (fn [{:keys [this props state]}]
     (when-let [get-dom-node (:get-anchor-dom-node props)]
       (let [rect (.getBoundingClientRect (get-dom-node))]
         (swap! state assoc :position {:top (+ (.-top rect) js/document.body.scrollTop)
                                       :left (+ (.-left rect) js/document.body.scrollLeft)})))
     (when-let [get-first (:get-first-element-dom-node props)]
       (common/focus-and-select (get-first))
       (when-let [get-last (:get-last-element-dom-node props)]
         (.addEventListener (get-first) "keydown" (common/create-key-handler [:tab] #(.-shiftKey %)
                                                    (fn [e] (.preventDefault e)
                                                      (when (:cycle-focus? props)
                                                        (.focus (get-last))))))
         (.addEventListener (get-last) "keydown" (common/create-key-handler [:tab] #(not (.-shiftKey %))
                                                   (fn [e] (.preventDefault e)
                                                     (when (:cycle-focus? props)
                                                       (.focus (get-first))))))))
     (set! (.-onKeyDownHandler this)
       (common/create-key-handler [:esc] #((:dismiss-self props))))
     (.addEventListener js/window "keydown" (.-onKeyDownHandler this)))
   :component-will-unmount
   (fn [{:keys [this]}]
     (.removeEventListener js/window "keydown" (.-onKeyDownHandler this)))})


(react/defc OKCancelForm
  {:get-default-props
   (fn []
     {:show-cancel? true})
   :render
   (fn [{:keys [props]}]
     (let [{:keys [header content dismiss-self ok-button show-cancel? cancel-text]} props]
       [:div {}
        [:div {:style {:borderBottom style/standard-line
                       :padding "20px 48px 18px"
                       :fontSize "137%" :fontWeight 400 :lineHeight 1}}
         header]
        [:div {:style {:padding "22px 48px 40px" :backgroundColor (:background-gray style/colors)}}
         content
         [:div {:style {:marginTop 40 :textAlign "center"}}
          (when show-cancel?
            [:a {:style {:marginRight 27 :marginTop 2 :padding "0.5em"
                         :display "inline-block"
                         :fontSize "106%" :fontWeight 500 :textDecoration "none"
                         :color (:button-blue style/colors)}
                 :href "javascript:;"
                 :onClick dismiss-self
                 :onKeyDown (common/create-key-handler [:space :enter] dismiss-self)}
             (or cancel-text "Cancel")])
          ok-button]]]))})


(react/defc GCSFilePreviewLink
  {:render
   (fn [{:keys [props state this]}]
     (assert (:bucket-name props) "No bucket name provided")
     (assert (:object props) "No GCS object provided")
     [:div (if-let [style-props (:style-props props)] style-props {})
      [:a {:href "javascript:;"
           :onClick #(react/call :show-dialog this)}
       (:object props)]
      (when (or (:show-dialog? @state) (:loading? @state))
        (let [{:keys [data error]} (:response @state)
              data-size (when data (data "size"))
              labeled (fn [label & contents]
                        [:div {}
                         [:div {:style {:display "inline-block" :width 120}} (str label ": ")]
                         contents])]
          [:div {:style {:position "fixed" :top 0 :left 0 :right 0 :bottom 0 :zIndex 9999
                         :fontSize "initial" :fontWeight "initial"}}
           [Dialog
            {:dismiss-self #(swap! state dissoc :show-dialog?)
             :width "75%"
             :content
             (react/create-element
               [OKCancelForm
                {:header "File Details"
                 :content (react/create-element
                            [:div {:style {:overflow "auto"}}
                             (labeled "Google Bucket" (:bucket-name props))
                             (labeled "Object" (:object props))
                             (when (:loading? @state)
                               [Spinner {:text "Getting file info..."}])
                             (when data
                               [:div {:style {:marginTop "1em"}}
                                (labeled "File size"
                                  (common/format-filesize data-size)
                                  [:span {:style {:marginLeft "1em"}}
                                   [:a {:href (common/gcs-object->download-url (:bucket-name props) (:object props))
                                    :target "_blank"} "Open"] [:span {:style {:fontStyle "italic" :color (:text-gray style/colors)}}" (right-click to download)"]]
                                  (when (> data-size 100000000)
                                    [:span {:style {:color (:exception-red style/colors) :marginLeft "2ex"}}
                                     (icons/font-icon {:style {:fontSize "100%" :verticalAlign "middle" :marginRight "1ex"}}
                                       :status-warning-triangle)
                                     "Warning: Downloading this file may incur a large data egress charge"]))
                                (if (:show-details? @state)
                                  [:div {}
                                   (labeled "Created" (common/format-date (data "timeCreated")))
                                   (labeled "Updated" (common/format-date (data "updated")))
                                   (labeled "MD5" (data "md5Hash"))
                                   (style/create-link {:text "Collapse"
                                                       :onClick #(swap! state dissoc :show-details?)})]
                                  (style/create-link {:text "More info"
                                                      :onClick #(swap! state assoc :show-details? true)}))])
                             (when error
                               [:div {:style {:marginTop "1em"}}
                                [:span {:style {:color (:exception-red style/colors)}} "Error! "]
                                "This file was not found."
                                (if (:show-error-details? @state)
                                  [:div {}
                                   [:pre {} error]
                                   (style/create-link {:text "Hide detail"
                                                       :onClick #(swap! state dissoc :show-error-details?)})]
                                  [:div {}
                                   (style/create-link {:text "Show full error response"
                                                       :onClick #(swap! state assoc :show-error-details? true)})])])])
                 :dismiss-self #(swap! state dissoc :show-dialog?)
                 :show-cancel? false
                 :ok-button [Button {:text "Done" :onClick #(swap! state dissoc :show-dialog?)}]}])}]]))])
   :show-dialog
   (fn [{:keys [state props]}]
     (if (:response @state)
       (swap! state assoc :show-dialog? true)
       (do
         (swap! state assoc :loading? true)
         (endpoints/call-ajax-orch
           {:endpoint (endpoints/get-gcs-stats (:bucket-name props) (:object props))
            :on-done (fn [{:keys [success? get-parsed-response xhr]}]
                       (swap! state assoc :show-dialog? true :loading? false
                         :response (if success? {:data (get-parsed-response)}
                                                {:error (.-responseText xhr)})))}))))})
