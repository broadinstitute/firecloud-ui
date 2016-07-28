(ns org.broadinstitute.firecloud-ui.common.dialog
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :refer [Spinner Button]]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
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


(react/defc GCSFilePreviewLink
  (let [Dialog
        (react/create-class
          {:render
           (fn [{:keys [props state]}]
             [modal/OKCancelForm
              {:header "File Details"
               :content
               (let [{:keys [data error]} (:response @state)
                     data-size (when data (data "size"))
                     labeled (fn [label & contents]
                               [:div {}
                                [:div {:style {:display "inline-block" :width 120}} (str label ": ")]
                                contents])]
                 [:div {:style {:width 500 :overflow "auto"}}
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
                                    :target "_blank"}
                                "Open"]
                               [:span {:style {:fontStyle "italic" :color (:text-gray style/colors)}}
                                " (right-click to download)"]]
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
               :show-cancel? false
               :ok-button {:text "Done" :onClick modal/pop-modal}}])
           :component-did-mount
           (fn [{:keys [props state]}]
             (swap! state assoc :loading? true)
             (endpoints/call-ajax-orch
               {:endpoint (endpoints/get-gcs-stats (:bucket-name props) (:object props))
                :on-done (fn [{:keys [success? get-parsed-response xhr]}]
                           (swap! state assoc
                                  :loading? false
                                  :response (if success?
                                              {:data (get-parsed-response)}
                                              {:error (.-responseText xhr)})))}))})]
    {:render
     (fn [{:keys [props]}]
       (assert (:bucket-name props) "No bucket name provided")
       (assert (:object props) "No GCS object provided")
       [:div (if-let [style-props (:style-props props)] style-props {})
        [:a {:href "javascript:;" :onClick #(modal/push-modal [Dialog props])}
         (:object props)]])}))
