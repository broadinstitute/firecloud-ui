(ns broadfcui.page.method-repo.create-method
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.codemirror :refer [CodeMirror]]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(defn- build-defaults [{:keys [duplicate]}]
  (cond duplicate (merge {:header "Duplicate Method"
                          :name (str "Copy of " (:name duplicate))
                          :wdl (:payload duplicate)}
                         (select-keys duplicate [:synopsis :documentation]))
        :else {:header "Create New Method"}))


(react/defc CreateMethodDialog
  {:render
   (fn [{:keys [props state refs this]}]
     (let [defaults (build-defaults props)]
       [comps/OKCancelForm
        {:header (:header defaults)
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
            [:div {:style {:flex "1 0 auto"}}
             (style/create-form-label "Name")
             [input/TextField {:ref "name" :style {:width "100%"}
                               :defaultValue (:name defaults)
                               :predicates [(input/nonempty "Method name")]}]]]
           ;;GAWB-1897 removes Type field and makes all MC types "Workflow" until "Task" type is supported
           (style/create-form-label "Synopsis (optional)")
           (style/create-text-field {:ref "synopsis"
                                     :defaultValue (:synopsis defaults)
                                     :style {:width "100%"}})
           (style/create-form-label "Documentation (optional)")
           (style/create-text-area {:ref "documentation"
                                    :defaultValue (:documentation defaults)
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
                                             (this :set-wdl-text text)))
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
                                      :onClick #(this :set-wdl-text (:file-contents @state))})])
               [:span {:style {:flex "1 0 auto"}}]
               (link "undo" undo?)
               (link "redo" redo?)]))
           [CodeMirror {:ref "wdl-editor" :text (:wdl defaults) :read-only? false}]

           [comps/ErrorViewer {:error (:upload-error @state)}]
           (style/create-validation-error-message (:validation-errors @state))
           [:div {:style {:marginTop "0.8em" :fontSize "88%"}} "WDL must use Docker image digests to allow call caching"
            (common/question-icon-link "Guide to Call Caching" (config/call-caching-guide-url))]])
         :ok-button (react/create-element
                     [comps/Button {:ref "ok-button" :text "Upload" :onClick #(this :create-method)}])}]))
   :component-did-mount
   (fn [{:keys [state refs]}]
     ((@refs "wdl-editor") :add-listener "change"
      #(swap! state assoc :undo-history
              (js->clj ((@refs "wdl-editor") :call-method "historySize")))))
   :set-wdl-text
   (fn [{:keys [refs]} text]
     ((@refs "wdl-editor") :call-method "setValue" text))
   :create-method
   (fn [{:keys [props state refs]}]
     (let [[namespace name & fails] (input/get-and-validate refs "namespace" "name")
           [synopsis documentation] (common/get-text refs "synopsis" "documentation")
           wdl ((@refs "wdl-editor") :call-method "getValue")
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
                     :entityType "Workflow"}
           :headers utils/content-type=json
           :on-done
           (fn [{:keys [success? get-parsed-response]}]
             (swap! state dissoc :uploading?)
             (if success?
               (do
                 (modal/pop-modal)
                 (let [response (get-parsed-response)
                       {:keys [namespace name snapshotId]} response
                       id {:namespace namespace :name name :snapshot-id snapshotId}]
                   ((:on-created props) :method id)))
               (swap! state assoc :upload-error (get-parsed-response false))))}))))})
