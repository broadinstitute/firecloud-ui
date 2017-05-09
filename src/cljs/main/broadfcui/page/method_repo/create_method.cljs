(ns broadfcui.page.method-repo.create-method
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.codemirror :refer [CodeMirror]]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.input :as input]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(defn- build-info [{:keys [duplicate snapshot]}]
  (cond duplicate (merge {:header "Clone Method"
                          :name (str (:name duplicate) "_copy")
                          :ok-text "Create New Method"}
                         (select-keys duplicate [:synopsis :documentation :payload]))
        snapshot (merge {:header "Edit Method"
                         :ok-text "Create New Snapshot"
                         :locked #{:namespace :name}
                         :show-redact? true}
                        (select-keys snapshot [:namespace :name :snapshotId :synopsis :documentation :payload]))
        :else {:header "Create New Method"
               :ok-text "Upload"}))


(react/defc CreateMethodDialog
  {:component-will-mount
   (fn [{:keys [props locals]}]
     (swap! locals assoc :info (build-info props)))
   :render
   (fn [{:keys [state refs locals this]}]
     (let [{:keys [info]} @locals]
       [comps/OKCancelForm
        {:header (:header info)
         :get-first-element-dom-node #(react/find-dom-node (@refs "namespace"))
         :get-last-element-dom-node #(react/find-dom-node (@refs "ok-button"))
         :content
         (react/create-element
          [:div {:style {:width "80vw"}}
           (when-let [banner (:banner @state)]
             [comps/Blocker {:banner banner}])

           [:div {:style {:display "flex" :justifyContent "space-between"}}
            [:div {:style {:flex "1 0 auto" :marginRight "1em"}}
             (style/create-form-label "Namespace")
             [input/TextField {:ref "namespace" :style {:width "100%"}
                               :defaultValue (:namespace info)
                               :disabled (contains? (:locked info) :namespace)
                               :predicates [(input/nonempty "Method namespace")]}]]
            [:div {:style {:flex "1 0 auto"}}
             (style/create-form-label "Name")
             [input/TextField {:ref "name" :style {:autoFocus true :width "100%"}
                               :defaultValue (:name info)
                               :disabled (contains? (:locked info) :name)
                               :predicates [(input/nonempty "Method name")]}]]]
           ;;GAWB-1897 removes Type field and makes all MC types "Workflow" until "Task" type is supported
           (style/create-form-label "Synopsis (optional, 80 characters max)")
           (style/create-text-field {:ref "synopsis"
                                     :defaultValue (:synopsis info)
                                     :maxLength 80
                                     :style {:width "100%"}})
           (style/create-form-label "Documentation (optional)")
           (style/create-text-area {:ref "documentation"
                                    :defaultValue (:documentation info)
                                    :style {:width "100%"}
                                    :rows 5})

           [:input {:type "file" :ref "wdl-uploader" :style {:display "none"}
                    :onChange (fn [e]
                                (let [file (-> e .-target .-files (aget 0))
                                      reader (js/FileReader.)]
                                  (when file
                                    (set! (.-onload reader)
                                          #(let [text (.-result reader)]
                                             (swap! state assoc :file-name (.-name file) :file-contents text)
                                             (this :-set-wdl-text text)))
                                    (.readAsText reader file))))}]
           (style/create-form-label
            (let [{:keys [file-name]
                   {:strs [undo redo]} :undo-history} @state
                  [undo? redo?] (map pos? [undo redo])
                  link (fn [label enabled?]
                         (if enabled?
                           (style/create-link {:text (clojure.string/capitalize label)
                                               :onClick #((@refs "wdl-editor") :call-method label)
                                               :style {:color (:text-light style/colors)
                                                       :backgroundColor "white"
                                                       :padding "0 6px"
                                                       :border style/standard-line}})
                           [:span {:style {:color (:text-lighter style/colors)
                                           :padding "0 6px"
                                           :border style/standard-line}}
                            (clojure.string/capitalize label)]))]
              [:div {:style {:display "flex" :alignItems "baseline" :width "100%"}}
               [:span {:style {:paddingRight "1em"}} "WDL"]
               (style/create-link {:text "Load from file..."
                                   :onClick #(.click (@refs "wdl-uploader"))})
               (when file-name
                 [:span {}
                  [:span {:style {:padding "0 1em 0 25px"}} (str "Selected: " file-name)]
                  (style/create-link {:text "Reset to file"
                                      :onClick #(this :-set-wdl-text (:file-contents @state))})])
               [:span {:style {:flex "1 0 auto"}}]
               (link "undo" undo?)
               (link "redo" redo?)]))
           [CodeMirror {:ref "wdl-editor" :text (:payload info) :read-only? false}]
           [:div {:style {:marginTop "0.8em" :fontSize "88%"}}
            "WDL must use Docker image digests to allow call caching "
            [:a {:target "_blank" :href (str (config/call-caching-guide-url))}
             "Learn about call caching" icons/external-link-icon]]

           (when (:show-redact? info)
             [:div {:style {:textAlign "center"}}
              [comps/Checkbox {:ref "redact-checkbox"
                               :label (str "Redact Snapshot " (:snapshotId info))}]])

           [comps/ErrorViewer {:error (:upload-error @state)}]
           (style/create-validation-error-message (:validation-errors @state))])
         :ok-button (react/create-element
                     [comps/Button {:ref "ok-button"
                                    :text (:ok-text info)
                                    :onClick #(this :-create-method)}])}]))
   :component-did-mount
   (fn [{:keys [state refs]}]
     ((@refs "wdl-editor") :add-listener "change"
      #(swap! state assoc :undo-history
              (js->clj ((@refs "wdl-editor") :call-method "historySize")))))
   :-set-wdl-text
   (fn [{:keys [refs]} text]
     ((@refs "wdl-editor") :call-method "setValue" text))
   :-create-method
   (fn [{:keys [state refs this]}]
     (let [[namespace name & fails] (input/get-and-validate refs "namespace" "name")
           [synopsis documentation] (common/get-text refs "synopsis" "documentation")
           wdl ((@refs "wdl-editor") :call-method "getValue")
           fails (or fails (when (clojure.string/blank? wdl) ["Please enter the WDL payload"]))]
       (swap! state assoc :validation-errors fails)
       (when-not fails
         (swap! state assoc :banner "Uploading...")
         (endpoints/call-ajax-orch
          {:endpoint endpoints/post-method
           :payload {:namespace namespace
                     :name name
                     :synopsis synopsis
                     :documentation documentation
                     :payload wdl
                     :entityType "Workflow"}
           :headers utils/content-type=json
           :on-done
           (fn [{:keys [success? get-parsed-response]}]
             (if success?
               (this :-after-upload (get-parsed-response))
               (swap! state assoc
                      :banner nil
                      :upload-error (get-parsed-response false))))}))))
   :-after-upload
   (fn [{:keys [refs locals this]} parsed-response]
     (let [{:keys [namespace name snapshotId]} parsed-response
           new-entity-id {:namespace namespace :name name :snapshot-id snapshotId}]
       (if (and (:show-redact? (:info @locals))
                ((@refs "redact-checkbox") :checked?))
         (this :-redact-old-method new-entity-id)
         (this :-complete new-entity-id))))
   :-redact-old-method
   (fn [{:keys [state locals this]} new-entity-id]
     (swap! state assoc :banner "Redacting old method...")
     (let [{:keys [namespace name snapshotId]} (:info @locals)]
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/delete-agora-entity false namespace name snapshotId)
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (this :-complete new-entity-id
                          (when-not success? (get-parsed-response false))))})))
   :-complete
   (fn [{:keys [props]} new-entity-id & [redact-error]]
     (modal/pop-modal)
     ((:on-created props) :method new-entity-id)
     (when redact-error
       (comps/push-error-response redact-error)))})
