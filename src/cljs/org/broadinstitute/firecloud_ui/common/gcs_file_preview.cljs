(ns org.broadinstitute.firecloud-ui.common.gcs-file-preview
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :refer [Spinner]]
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
             data-size (:size data)
             cost (:estimatedCostUSD data)
             labeled (fn [label & contents]
                       [:div {}
                        [:div {:style {:display "inline-block" :width 185}} (str label ": ")]
                        contents])]
         [:div {:style {:width 700 :overflow "auto"}}
          (labeled "Google Bucket" (:bucket-name props))
          (labeled "Object" (:object props))
          [:div {:style {:marginTop "1em"}}
           (if (> data-size preview-byte-count) (str "Last " (:preview-line-count @state)
                                       " lines of log are shown. Use the link below to view the full log.") "Log:")
           ;; The max-height of 206 looks random, but it's so that the top line of the log preview is half cut-off
           ;; to hint to the user that they should scroll up.
           (react/create-element
             [:div {:ref "preview" :style {:marginTop "1em" :whiteSpace "pre-wrap" :fontFamily "monospace"
                                           :fontSize "90%" :overflowY "auto" :maxHeight 206
                                           :backgroundColor "#fff" :padding "1em" :borderRadius 8}}
              (str (if (> data-size preview-byte-count) "...") (:preview @state))])]
          (when (:loading? @state)
            [Spinner {:text "Getting file info..."}])
          (when data
            [:div {:style {:marginTop "1em"}}
             (labeled "File size"
                      (common/format-filesize data-size)
                      [:span {:style {:marginLeft "1em"}}
                       [:a {:href (common/gcs-object->download-url (:bucket-name props) (:object props))
                            :onClick #(utils/set-access-token-cookie (utils/get-access-token))
                            :target "_blank"}
                        "Open"]
                       [:span {:style {:fontStyle "italic" :color (:text-light style/colors)}}
                        " (right-click to download)"]])
             (labeled "Estimated download fee"
                      (if (nil? cost) "Unknown" (common/format-price cost))
                      [:span {:style {:marginLeft "1em"}}
                       [:span {:style {:fontStyle "italic" :color (:text-light style/colors)}}
                        " (non-US destinations may be higher)"]])
             (if (:show-details? @state)
               [:div {}
                (labeled "Created" (common/format-date (:timeCreated data)))
                (labeled "Updated" (common/format-date (:updated data)))
                (labeled "MD5" (:md5Hash data))
                (style/create-link {:text "Collapse"
                                    :onClick #(swap! state dissoc :show-details?)})]
               (style/create-link {:text "More info"
                                   :onClick #(swap! state assoc :show-details? true)}))])
          (when error
            [:div {:style {:marginTop "1em"}}
             [:span {:style {:color (:exception-state style/colors)}} "Error! "]
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
   (fn [{:keys [props state refs after-update]}]
     (swap! state assoc :loading? true)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-gcs-stats (:bucket-name props) (:object props))
       :on-done (fn [{:keys [success? get-parsed-response xhr status-code]}]
                  (swap! state assoc
                         :loading? false
                         :response (if success?
                                     {:data (get-parsed-response)}
                                     {:error (.-responseText xhr)
                                      :status status-code})))})
     (utils/ajax {:url (str "https://www.googleapis.com/storage/v1/b/" (:bucket-name props) "/o/"
                            (js/encodeURIComponent (:object props)) "?alt=media")
                  :headers {"Authorization" (str "Bearer " (utils/get-access-token))
                            "Range" (str "bytes=-" preview-byte-count)}
                  :on-done (fn [{:keys [success? status-text raw-response]}]
                             (swap! state assoc :preview raw-response
                                    :preview-line-count (count (clojure.string/split raw-response #"\n+")))
                             (after-update
                               (fn []
                                 (aset (@refs "preview") "scrollTop" (aget (@refs "preview") "scrollHeight")))))}))})


(def preview-byte-count 1800)

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
