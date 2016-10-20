(ns org.broadinstitute.firecloud-ui.page.method-repo.create-method
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.codemirror :refer [CodeMirror]]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc CreateMethodDialog
  {:render
   (fn [{:keys [state refs this]}]
     [modal/OKCancelForm
      {:header "Create New Method"
       :get-first-element-dom-node #(react/find-dom-node (@refs "namespace"))
       :get-last-element-dom-node #(react/find-dom-node (@refs "ok-button"))
       :content
       (react/create-element
        [:div {:style {:width "80vw"}}
         (when (:uploading? @state)
           [comps/Blocker {:banner "Uploading..."}])

         [:div {:style {:display "flex" :justifyContent "space-between"}}
          [:div {:style {:flex "1 0 auto" :marginRight "1em"}}
           (style/create-form-label "Namespace")
           [input/TextField {:ref "namespace" :style {:width "100%"}
                             :predicates [(input/nonempty "Method namespace")]}]]
          [:div {:style {:flex "1 0 auto" :marginRight "1em"}}
           (style/create-form-label "Name")
           [input/TextField {:ref "name" :style {:width "100%"}
                             :predicates [(input/nonempty "Method name")]}]]
          [:div {:style {:flex "0 0 100px"}}
           (style/create-form-label "Type")
           (style/create-identity-select {:ref "type"} ["Task" "Workflow"])]]

         (style/create-form-label "Synopsis (optional)")
         (style/create-text-field {:ref "synopsis" :style {:width "100%"}})
         (style/create-form-label "Documentation (optional)")
         (style/create-text-area {:ref "documentation" :style {:width "100%"} :rows 5})

         [:input {:type "file" :ref "wdl-uploader" :style {:display "none"}
                  :onChange (fn [e]
                              (let [file (-> e .-target .-files (aget 0))
                                    reader (js/FileReader.)]
                                (when file
                                  (set! (.-onload reader)
                                        #(let [text (.-result reader)]
                                          (swap! state assoc :file-name (.-name file) :file-contents text)
                                          (react/call :set-wdl-text this text)))
                                  (.readAsText reader file))))}]
         (style/create-form-label
          (let [{:keys [file-name]
                 {:strs [undo redo]} :undo-history} @state
                [undo? redo?] (map pos? [undo redo])
                link (fn [label enabled?]
                       (if enabled?
                         (style/create-link {:text (clojure.string/capitalize label)
                                             :onClick #(react/call :call-method (@refs "wdl-editor") label)
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
                                    :onClick #(react/call :set-wdl-text this (:file-contents @state))})])
             [:span {:style {:flex "1 0 auto"}}]
             (link "undo" undo?)
             (link "redo" redo?)]))
         [CodeMirror {:ref "wdl-editor" :read-only? false}]

         [comps/ErrorViewer {:error (:upload-error @state)}]
         (style/create-validation-error-message (:validation-errors @state))])
       :ok-button (react/create-element
                   [comps/Button {:ref "ok-button" :text "Upload" :onClick #(react/call :create-method this)}])}])
   :component-did-mount
   (fn [{:keys [state refs]}]
     (react/call :add-listener (@refs "wdl-editor") "change"
                 #(swap! state assoc :undo-history
                         (js->clj (react/call :call-method (@refs "wdl-editor") "historySize")))))
   :set-wdl-text
   (fn [{:keys [refs]} text]
     (react/call :call-method (@refs "wdl-editor") "setValue" text))
   :create-method
   (fn [{:keys [props state refs]}]
     (let [[namespace name & fails] (input/get-and-validate refs "namespace" "name")
           [synopsis documentation type] (common/get-text refs "synopsis" "documentation" "type")
           wdl (react/call :call-method (@refs "wdl-editor") "getValue")
           fails (or fails (when (clojure.string/blank? wdl) ["Please enter the WDL payload"]))]
       (swap! state assoc :validation-errors fails)
       (when-not fails
         (swap! state assoc :uploading? true)
         (endpoints/call-ajax-orch
          {:endpoint endpoints/post-method
           :payload {:namespace namespace
                     :name name
                     :synopsis synopsis
                     :documentation documentation
                     :payload wdl
                     :entityType type}
           :headers utils/content-type=json
           :on-done
           (fn [{:keys [success? get-parsed-response]}]
             (swap! state dissoc :uploading?)
             (if success?
               (do (modal/pop-modal) ((:on-success props) (get-parsed-response)))
               (swap! state assoc :upload-error (get-parsed-response))))}))))})
