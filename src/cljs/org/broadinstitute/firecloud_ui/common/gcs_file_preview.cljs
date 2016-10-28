(ns org.broadinstitute.firecloud-ui.common.gcs-file-preview
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :refer [Spinner]]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc PreviewDialog
  {:render
   (fn [{:keys [props state]}]
     [modal/OKCancelForm
      {:header "File Details"
       :content
       (let [{:keys [data error status]} (:response @state)
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
                         (icons/icon {:style {:fontSize "100%" :verticalAlign "middle" :marginRight "1ex"}}
                                     :warning-triangle)
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
             (case status
               404 "This file was not found."
               403 "You do not have access to this file."
               "See details below.")
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
        :on-done (fn [{:keys [success? get-parsed-response xhr status-code]}]
                   (swap! state assoc
                          :loading? false
                          :response (if success?
                                      {:data (get-parsed-response)}
                                      {:error (.-responseText xhr)
                                       :status status-code})))}))})


(react/defc GCSFilePreviewLink
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [bucket-name object workspace-bucket link-label]} props]
       (assert bucket-name "No bucket name provided")
       (assert object "No GCS object provided")
       [:div (or (:attributes props) {})
        [:a {:href "javascript:;" :onClick #(modal/push-modal [PreviewDialog props])}
         (if (= bucket-name workspace-bucket)
           object
           (if link-label (str link-label) (str "gs://" bucket-name "/" object)))]]))})
