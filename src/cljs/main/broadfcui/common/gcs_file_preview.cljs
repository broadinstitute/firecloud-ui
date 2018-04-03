(ns broadfcui.common.gcs-file-preview
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.test :as test-utils]
   [broadfcui.utils.user :as user]
   ))

(def ^:private preview-byte-count 20000)

(react/defc- PreviewDialog
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [dismiss object bucket-name]} props]
       [modals/OKCancelForm
        {:header "File Details"
         :data-test-id "preview-modal"
         :data-test-state (if (or object (:error (:response @state))) "ready" "loading")
         :dismiss dismiss
         :content
         (let [{:keys [data error status]} (:response @state)
               data-size (:size data)
               cost (:estimatedCostUSD data)
               labeled (fn [label & contents]
                         [:div {}
                          [:div {:style {:display "inline-block" :width 185}} (str label ": ")]
                          [:span {:data-test-id (test-utils/text->test-id label "content")} contents]])
               data-empty (or (= data-size "0") (string/blank? data-size))
               bam? (re-find #"\.ba[mi]$" object)
               img? (re-find #"\.(?:(jpe?g|png|gif|bmp))$" object)
               hide-preview? (or bam? img?)]
           [:div {:style {:width 700 :overflow "auto"}}
            (labeled "Google Bucket" bucket-name)
            (labeled "Object" object)
            [:div {:style {:marginTop "1em"}}
             [:div {:data-test-id "preview-message"}
              (if hide-preview?
                "Preview is not supported for this filetype."
                "Previews may not be supported for some filetypes.")]
             (when (and (not hide-preview?) (> data-size preview-byte-count))
               (str "Last " (:preview-line-count @state) " lines are shown. Use link below to view entire file."))
             ;; The max-height of 206 looks random, but it's so that the top line of the log preview is half cut-off
             ;; to hint to the user that they should scroll up.
             (when-not (or data-empty hide-preview?)
               (react/create-element
                [:div {:data-test-id "preview-pane"
                       :ref "preview"
                       :style {:marginTop "1em" :whiteSpace "pre-wrap" :fontFamily "monospace"
                               :fontSize "90%" :overflowY "auto" :maxHeight 206
                               :backgroundColor "#fff" :padding "1em" :borderRadius 8}}
                 (str (if (> data-size preview-byte-count) "...") (:preview @state))]))]
            (when (:loading? @state)
              (spinner "Getting file info..."))
            (when data
              [:div {:style {:marginTop "1em"}}
               (labeled "File size"
                        (common/format-filesize data-size)
                        (if data-empty
                          (react/create-element [:span {:style {:marginLeft "2em" :fontWeight "bold"}} "File Empty"])
                          (react/create-element
                           [:span {:style {:marginLeft "1em"}}
                            (links/create-external {:href (common/gcs-object->download-url bucket-name object)
                                                    :onClick user/refresh-access-token
                                                    :onContextMenu user/refresh-access-token}
                              "Open")
                            [:span {:style {:fontStyle "italic" :color (:text-light style/colors)}}
                             " (right-click to download)"]]))
                        (when (> data-size 100000000)
                          [:div {:style {:marginTop "1em" :marginBottom "1em"}}
                           [:div {} "Downloading large files through the browser may not be successful. Instead use this gsutil"]
                           [:div {:style {:marginBottom ".5em"}} "command replacing [DESTINATION] with the local file path you wish to download to."]
                           (style/create-code-sample
                            (str "gsutil cp gs://" bucket-name "/" object " [DESTINATION]"))
                           [:div {:style {:marginTop "1em"}} "For more information on the gsutil tool click "
                            (links/create-external {:href "https://cloud.google.com/storage/docs/gsutil"} "here")]]))
               (when-not data-empty
                 (labeled "Estimated download fee"
                          (if (nil? cost) "Unknown" (common/format-price cost))
                          [:span {:style {:marginLeft "1em"}}
                           [:span {:style {:fontStyle "italic" :color (:text-light style/colors)}}
                            " (non-US destinations may be higher)"]]))
               (if (:show-details? @state)
                 [:div {}
                  (labeled "Created" (common/format-date (:timeCreated data)))
                  (labeled "Updated" (common/format-date (:updated data)))
                  (labeled "MD5" (:md5Hash data))
                  (links/create-internal {:onClick #(swap! state dissoc :show-details?)} "Collapse")]
                 (links/create-internal {:onClick #(swap! state assoc :show-details? true)} "More info"))])
            (when error
              [:div {:data-test-id "error-message"
                     :style {:marginTop "1em"}}
               [:span {:style {:color (:state-exception style/colors)}} "Error! "]
               (case status
                 404 "This file was not found."
                 403 "You do not have access to this file."
                 "See details below.")
               (if (:show-error-details? @state)
                 [:div {}
                  [:pre {} error]
                  (links/create-internal {:onClick #(swap! state dissoc :show-error-details?)} "Hide detail")]
                 [:div {}
                  (links/create-internal {:onClick #(swap! state assoc :show-error-details? true)} "Show full error response")])])])
         :show-cancel? false
         :ok-button {:text "Done" :onClick dismiss}}]))
   :component-did-mount
   (fn [{:keys [props state refs after-update]}]
     (let [{:keys [object bucket-name]} props]
       (swap! state assoc :loading? true)
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-gcs-stats bucket-name object)
         :on-done (fn [{:keys [success? get-parsed-response xhr status-code]}]
                    (swap! state assoc
                           :loading? false
                           :response (if success?
                                       {:data (get-parsed-response)}
                                       {:error (.-responseText xhr)
                                        :status status-code})))})
       (ajax/call {:url (str "https://www.googleapis.com/storage/v1/b/" bucket-name "/o/"
                             (js/encodeURIComponent object) "?alt=media")
                   :headers (merge (user/get-bearer-token-header)
                                   {"Range" (str "bytes=-" preview-byte-count)})
                   :on-done (fn [{:keys [raw-response]}]
                              (swap! state assoc :preview raw-response
                                     :preview-line-count (count (clojure.string/split raw-response #"\n+")))
                              (after-update
                               (fn []
                                 (when-not (string/blank? (@refs "preview"))
                                   (aset (@refs "preview") "scrollTop" (aget (@refs "preview") "scrollHeight"))))))})))})

(react/defc DOSPreviewDialog
  {:component-will-mount
   (fn [{:keys [state props]}]
     (let [{:keys [dos-uri]} props]
       (swap! state assoc :translating-dos? true)
       (ajax/call-martha (utils/->json-string {:url dos-uri, :pattern "gs://"})
         {:on-done (fn [{:keys [success? raw-response xhr status-code get-parsed-response]}]
                     (swap! state assoc
                       :translating-dos? false
                       :loading? true
                       :showing-preview? true
                       :response (if success?
                                   {:data raw-response}
                                   {:error xhr
                                    :status status-code
                                    :raw-response raw-response
                                    :success success?
                                    :parse-response get-parsed-response})))})))
   :render
   (fn [{:keys [state props]}]
     (let [{:keys [data error]} (:response @state)]
       [:div (or (:attributes props) {})
        (if (:translating-dos? @state)
          (spinner "Translating dos uri...")
          (when (:showing-preview? @state)
            (if-let [parsed (common/parse-gcs-uri data)]
              [PreviewDialog (assoc parsed
                               :dismiss (:dismiss props))]
              [PreviewDialog (assoc props :error error :object ""
                               :dismiss (:dismiss props))])))]))})

(react/defc GCSFilePreviewLink
  {:render
   (fn [{:keys [state props]}]
     (let [{:keys [bucket-name object workspace-bucket link-label]} props]
       (assert bucket-name "No bucket name provided")
       (assert object "No GCS object provided")
       [:div (or (:attributes props) {})
        (when (:showing-preview? @state)
          [PreviewDialog (assoc (utils/restructure bucket-name object)
                           :dismiss #(swap! state dissoc :showing-preview?))])
        [:a {:href (str "gs://" bucket-name "/" object)
             :onClick (fn [e]
                        (.preventDefault e)
                        (swap! state assoc :showing-preview? true))}
         (if (= bucket-name workspace-bucket)
           object
           (if link-label (str link-label) (str "gs://" bucket-name "/" object)))]]))})

(react/defc DOSFilePreviewLink
  {:render
   (fn [{:keys [state props]}]
     (let [{:keys [dos-uri link-label]} props]
       [:div (or (:attributes props) {})
        (when (:showing-preview? @state)
          [DOSPreviewDialog (assoc props
                              :dos-uri dos-uri
                              :dismiss #(swap! state dissoc :showing-preview?))])
        [:a {:href dos-uri
             :onClick (fn [e]
                        (.preventDefault e)
                        (swap! state assoc :showing-preview? true))}
         (if link-label (str link-label) dos-uri)]]))})

(react/defc FilePreviewLink
  {:render
   (fn [{:keys [props]}]
     (if (:dos-uri props)
       [DOSFilePreviewLink props]
       [GCSFilePreviewLink props]))})
