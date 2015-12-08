(ns org.broadinstitute.firecloud-ui.page.workspace.data.import-data
  (:require
    [clojure.string]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(def ^:private preview-limit 4096)

(react/defc Page
  {:render
   (fn [{:keys [state refs this]}]
     [:div {:style {:textAlign "center"}}
      (when (:loading? @state)
        [comps/Blocker {:banner "Uploading file..."}])

      [:input {:type "file" :name "entities" :ref "entities"
               :style {:display "none"}
               :onChange (fn [e]
                           (let [file (-> e .-target .-files (aget 0))
                                 reader (js/FileReader.)]
                             (when file
                               (swap! state assoc :upload-result nil)
                               (set! (.-onload reader)
                                 #(swap! state assoc :file file :file-contents (.-result reader)))
                               (.readAsText reader (.slice file 0 preview-limit)))))}]
      [comps/Button {:text "Choose file..." :onClick #(-> (@refs "entities") .getDOMNode .click)}]
      (when (:file-contents @state)
        [:div {:style {:margin "0.5em 2em" :padding "0.5em" :border (str "1px solid " (:line-gray style/colors))}}
         (str "Previewing '" (-> (:file @state) .-name) "':")
         [:div {:style {:overflow "auto" :maxHeight 200
                        :paddingBottom "0.5em" :textAlign "left"}}
          [:pre {} (:file-contents @state)]
          (when (> (.-size (:file @state)) preview-limit)
            [:em {} "(file truncated for preview)"])]])
      (when (and (:file @state) (not (:upload-result @state)))
        [comps/Button {:text "Upload"
                       :onClick #(react/call :do-upload this)}])
      (if-let [result (:upload-result @state)]
        (if (:success? result)
          [:div {:style {:paddingTop "1em"}}
           (icons/font-icon {:style {:fontSize "200%" :color (:success-green style/colors)}} :status-done)
           [:span {:style {:margin "-0.5em 0 0 1em"}} "Success!"]]
          [:div {:style {:paddingTop "1em"}}
           [comps/ErrorViewer {:error (:error result)}]]))])
   :component-did-mount
   (fn [{:keys [props]}]
     ((:push-crumb props) {:text "File"}))
   :component-will-unmount
   (fn [{:keys [props]}]
     ((:pop-crumb props)))
   :do-upload
   (fn [{:keys [props state]}]
     (swap! state assoc :loading? true)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/import-entities (:workspace-id props))
        :raw-data (utils/generate-form-data {:entities (:file @state)})
        :encType "multipart/form-data"
        :on-done (fn [{:keys [success? xhr get-parsed-response]}]
                   (swap! state dissoc :loading? :file :file-contents)
                   (if success?
                     (do
                       (swap! state assoc :upload-result {:success? true})
                       ((:reload-data-tab props) (.-responseText xhr)))
                     (swap! state assoc :upload-result {:success? false :error (get-parsed-response)})))}))})
