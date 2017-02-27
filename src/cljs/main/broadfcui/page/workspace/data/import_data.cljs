(ns broadfcui.page.workspace.data.import-data
  (:require
    [clojure.string]
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.config :as config]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.style :as style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.utils :as utils]
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
      [:div {:style {:marginBottom "0.8em"}} "For more information, see our "
       [:a {:href (config/user-guide-url) :target "_blank"} "user guide"] "."]
      [comps/Button {:text "Choose file..." :onClick #(-> (@refs "entities") .click)}]
      (when (:file-contents @state)
        [:div {:style {:margin "0.5em 2em" :padding "0.5em" :border style/standard-line}}
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
          (style/create-flexbox {:style {:justifyContent "center" :paddingTop "1em"}}
            (icons/icon {:style {:fontSize "200%" :color (:success-state style/colors)}} :done)
            [:span {:style {:marginLeft "1em"}} "Success!"])
          [:div {:style {:paddingTop "1em"}}
           [comps/ErrorViewer {:error (:error result)}]]))])
   :do-upload
   (fn [{:keys [props state]}]
     (swap! state assoc :loading? true)
     (endpoints/call-ajax-orch
       {:endpoint ((if (= "data" (:import-type props))
                     endpoints/import-entities
                     endpoints/import-attributes)
                    (:workspace-id props))
        :raw-data (utils/generate-form-data {(if (= "data" (:import-type props)) :entities :attributes) (:file @state)})
        :encType "multipart/form-data"
        :on-done (fn [{:keys [success? xhr get-parsed-response]}]
                  (swap! state dissoc :loading? :file :file-contents)
                  (if success?
                    (swap! state assoc :upload-result {:success? true})
                    (swap! state assoc :upload-result {:success? false :error (get-parsed-response false)})))}))})
