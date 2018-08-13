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
(def ^:private previewable-content-types #{"application/json" "text/plain"})

(defn- previewable?
  ([filename]
   (previewable? filename "unknown"))
  ([filename content-type]
   (let [binary? (re-find #"\.(?:(ba[mi]|cra[mi]|pac|sa|bwt))$" filename)
         img? (re-find #"\.(?:(jpe?g|png|gif|bmp|pdf))$" filename)
         compressed? (re-find #"\.gz$|\.gz\." filename)
         hide-preview-by-extension? (or binary? img? compressed?)
         previewable-content-type? (contains? previewable-content-types content-type)]
     (or
      previewable-content-type?
      (nil? hide-preview-by-extension?)))))

(react/defc- PreviewDialog
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [dismiss object bucket-name dos-uri]} props
           {:keys [response preview-line-count preview loading? show-details? show-error-details?]} @state
           {:keys [data error status]} response
           {:keys [md5Hash size updated timeCreated]} data
           object (or object (:name data))
           bucket-name (or bucket-name (:bucket data))]
       [modals/OKCancelForm
        {:header "File Details"
         :data-test-id "preview-modal"
         :data-test-state (if (or object error) "ready" "loading")
         :dismiss dismiss
         :content
         (if (and dos-uri (not response))
           (spinner "Resolving DOS uri...")
           (let [[parsed-error _] (when error (utils/parse-json-string error true false))
                 parsed-error-message (when parsed-error (:message parsed-error))
                 labeled (fn [label & contents]
                           [:div {}
                            [:div {:style {:display "inline-block" :width 185}} (str label ": ")]
                            [:span {:data-test-id (test-utils/text->test-id label "content")} contents]])
                 data-empty (= size 0)
                 hide-preview? (not (previewable? object))]
             [:div {:style {:width 700 :overflow "auto"}}
              (labeled "Google Bucket" bucket-name)
              (labeled "Object" object)
              [:div {:style {:marginTop "1em"}}
               [:div {:data-test-id "preview-message"}
                (if hide-preview?
                  "Preview is not supported for this filetype."
                  "Previews may not be supported for some filetypes.")]
               (when (and (not hide-preview?) (> size preview-byte-count))
                 (str "Last " preview-line-count " lines are shown. Use link below to view entire file."))
               ;; The max-height of 206 looks random, but it's so that the top line of the log preview is half cut-off
               ;; to hint to the user that they should scroll up.
               (when-not (or data-empty hide-preview?)
                 (react/create-element
                  [:div {:data-test-id (if preview "preview-pane" "loading-preview-pane")
                         :ref "preview"
                         :style {:marginTop "1em" :whiteSpace "pre-wrap" :fontFamily "monospace"
                                 :fontSize "90%" :overflowY "auto" :maxHeight 206
                                 :backgroundColor "#fff" :padding "1em" :borderRadius 8}}
                   (or preview (spinner "Loading preview..."))]))]
              (when loading?
                (spinner "Getting file info..."))
              (when data
                [:div {:style {:marginTop "1em"}}
                 (labeled "File size"
                          (common/format-filesize size)
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
                          (when (> size 100000000)
                            [:div {:style {:marginTop "1em" :marginBottom "1em"}}
                             [:div {} "Downloading large files through the browser may not be successful. Instead use this gsutil"]
                             [:div {:style {:marginBottom ".5em"}} "command replacing [DESTINATION] with the local file path you wish to download to."]
                             (style/create-code-sample
                              (str "gsutil cp gs://" bucket-name "/" object " [DESTINATION]"))
                             [:div {:style {:marginTop "1em"}} "For more information on the gsutil tool click "
                              (links/create-external {:href "https://cloud.google.com/storage/docs/gsutil"} "here")]]))
                 (when-not data-empty
                   (labeled "Estimated download fee"
                            (common/format-price (* size 1.1175870895385742e-10)) ; not actually a magic number, calculated from Google based on basic cost per byte (in cents)
                            [:span {:style {:marginLeft "1em"}}
                             [:span {:style {:fontStyle "italic" :color (:text-light style/colors)}}
                              " (non-US destinations may be higher)"]]))
                 (when (or timeCreated updated md5Hash)
                   (if show-details?
                     [:div {}
                      (some->> timeCreated common/format-date (labeled "Created"))
                      (some->> updated common/format-date (labeled "Updated"))
                      (some->> md5Hash (labeled "MD5"))
                      (links/create-internal {:onClick #(swap! state dissoc :show-details?)} "Collapse")]
                     (links/create-internal {:onClick #(swap! state assoc :show-details? true)} "More info")))])
              (when error
                [:div {:data-test-id "error-message"
                       :style {:marginTop "1em"}}
                 [:span {:style {:color (:state-exception style/colors)}} "Error! "]
                 (case status
                   404 "This file was not found."
                   403 "You do not have access to this file."
                   (or parsed-error-message "See details below."))
                 (if show-error-details?
                   [:div {}
                    [:pre {} error]
                    (links/create-internal {:onClick #(swap! state dissoc :show-error-details?)} "Hide detail")]
                   [:div {}
                    (links/create-internal {:onClick #(swap! state assoc :show-error-details? true)} "Show full error response")])])]))
         :show-cancel? false
         :ok-button {:text "Done" :onClick dismiss}}]))
   :component-did-mount
   (fn [{:keys [props state refs after-update]}]
     (let [{:keys [dos-uri gcs-uri]} props
           uri (or dos-uri gcs-uri)]
       (swap! state assoc :loading? true)
       (ajax/call-martha
        uri
        {:on-done (fn [{:keys [success? get-parsed-response xhr status-code]}]
                    (swap! state assoc
                           :loading? false
                           :response (if success?
                                       {:data (get-parsed-response)}
                                       {:error (.-responseText xhr)
                                        :status status-code}))
                    (when success?
                      (let [{:keys [contentType name bucket]} (get-parsed-response)
                            scopes common/storage-scopes]
                        (when (and gcs-uri (previewable? name contentType))
                          (endpoints/call-ajax-sam
                           {:endpoint (endpoints/pet-token (:workspace-namespace props))
                            :payload scopes
                            :headers ajax/content-type=json
                            :on-done
                            (fn [{:keys [success? raw-response get-parsed-response status-code status-text]}]
                              (if success?
                                ;; Sam endpoint returns a quoted token; dequote it.
                                (let [pet-token (utils/dequote raw-response)]
                                  (ajax/call {:url (str "https://www.googleapis.com/storage/v1/b/" bucket "/o/"
                                                        (js/encodeURIComponent name) "?alt=media")
                                              :headers {"Authorization" (str "Bearer " pet-token)
                                                        "Range" (str "bytes=-" preview-byte-count)}
                                              :on-done (fn [{:keys [raw-response]}]
                                                         (swap! state assoc :preview raw-response
                                                                :preview-line-count (count (clojure.string/split raw-response #"\n+")))
                                                         (after-update
                                                          (fn []
                                                            (when-not (string/blank? (@refs "preview"))
                                                              (aset (@refs "preview") "scrollTop" (aget (@refs "preview") "scrollHeight"))))))}))
                                (let [err-code (if (string/blank? status-text) status-code status-text)
                                      [error-json parsing-error] (get-parsed-response true false)
                                      trunc-response (subs raw-response 0 140)
                                      err-message (if parsing-error trunc-response (or (:message (get-parsed-response) trunc-response)))]
                                  (swap! state assoc :preview (str "Error reading preview (" err-code "): " err-message)))))})))))})))})

(react/defc- GCSFilePreviewLink
  {:render
   (fn [{:keys [state props]}]
     (let [{:keys [bucket-name object workspace-bucket link-label workspace-namespace gcs-uri]} props]
       (assert bucket-name "No bucket name provided")
       (assert object "No GCS object provided")
       [:div (or (:attributes props) {})
        (when (:showing-preview? @state)
          [PreviewDialog (assoc (utils/restructure bucket-name object workspace-namespace gcs-uri)
                           :dismiss #(swap! state dissoc :showing-preview?))])
        [:a {:href (str "gs://" bucket-name "/" object)
             :className "ltr-marked"
             :onClick (fn [e]
                        (.preventDefault e)
                        (swap! state assoc :showing-preview? true))}
         (if (= bucket-name workspace-bucket)
           object
           (if link-label (str link-label) (str "gs://" bucket-name "/" object)))]]))})

(react/defc- DOSFilePreviewLink
  {:render
   (fn [{:keys [state props]}]
     (let [{:keys [dos-uri link-label workspace-namespace]} props]
       [:div (or (:attributes props) {})
        (when (:showing-preview? @state)
          [PreviewDialog (assoc props
                           :workspace-namespace workspace-namespace
                           :dismiss #(swap! state dissoc :showing-preview?))])
        [:a {:href dos-uri
             :className "ltr-marked"
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
