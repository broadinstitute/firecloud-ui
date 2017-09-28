(ns broadfcui.page.method-repo.create-method
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.codemirror :refer [CodeMirror]]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(defn- build-info [{:keys [duplicate snapshot]}]
  (cond duplicate (merge {:header "Clone Method"
                          :name (str (:name duplicate) "_copy")
                          :ok-text "Create New Method"}
                         (select-keys duplicate [:synopsis :documentation :payload]))
        snapshot (merge {:header "Edit Method"
                         :ok-text "Save As New Snapshot"
                         :locked #{:namespace :name}
                         :edit-mode? true}
                        (select-keys snapshot [:namespace :name :snapshotId :synopsis :documentation :payload]))
        :else {:header "Create New Method"
               :ok-text "Upload"}))

(defn- build-new-entity-id [get-parsed-response]
  (let [{:keys [namespace name snapshotId]} (get-parsed-response)]
    (assoc (utils/restructure namespace name) :snapshot-id snapshotId)))


(react/defc CreateMethodDialog
  {:component-will-mount
   (fn [{:keys [props locals]}]
     (swap! locals assoc :info (build-info props)))
   :render
   (fn [{:keys [props state refs locals this]}]
     (let [{:keys [info]} @locals]
       [modals/OKCancelForm
        {:header (:header info)
         :dismiss (:dismiss props)
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
             [input/TextField {:data-test-id "namespace-field"
                               :ref "namespace" :style {:width "100%"}
                               :defaultValue (:namespace info)
                               :disabled (contains? (:locked info) :namespace)
                               :predicates [(input/nonempty "Method namespace")
                                            (input/alphanumeric_-period "Method namespace")]}]
             (style/create-textfield-hint input/hint-alphanumeric_-period)]
            [:div {:style {:flex "1 0 auto"}}
             (style/create-form-label "Name")
             [input/TextField {:data-test-id "name-field"
                               :ref "name" :style {:autoFocus true :width "100%"}
                               :defaultValue (:name info)
                               :disabled (contains? (:locked info) :name)
                               :predicates [(input/nonempty "Method name")
                                            (input/alphanumeric_-period "Method name")]}]
             (style/create-textfield-hint input/hint-alphanumeric_-period)]]
           ;;GAWB-1897 removes Type field and makes all MC types "Workflow" until "Task" type is supported
           (style/create-form-label "Synopsis (optional, 80 characters max)")
           (style/create-text-field {:data-test-id "synopsis-field"
                                     :ref "synopsis"
                                     :defaultValue (:synopsis info)
                                     :maxLength 80
                                     :style {:width "100%"}})
           (style/create-form-label "Documentation (optional)")
           (style/create-text-area {:data-test-id "documentation-field"
                                    :ref "documentation"
                                    :defaultValue (:documentation info)
                                    :style {:width "100%"}
                                    :rows 5})
           ;; This key is changed every time a file is selected causing React to completely replace the
           ;; element. Otherwise, if a user selects the same file (even after having modified it), the
           ;; browser will not fire the onChange event.
           [:input {:key (:file-input-key @state)
                    :type "file"
                    :ref "wdl-uploader"
                    :style {:display "none"}
                    :onChange (fn [e]
                                (let [file (-> e .-target .-files (aget 0))
                                      reader (js/FileReader.)]
                                  (when file
                                    (set! (.-onload reader)
                                          #(let [text (.-result reader)]
                                             (swap! state assoc
                                                    :file-name (.-name file)
                                                    :file-contents text
                                                    :file-input-key (gensym "wdl-uploader-"))
                                             (this :-set-wdl-text text)))
                                    (.readAsText reader file))))}]
           (style/create-form-label
            (let [{:keys [file-name]
                   {:strs [undo redo]} :undo-history} @state
                  [undo? redo?] (map pos? [undo redo])
                  link (fn [label enabled?]
                         (if enabled?
                           (links/create-internal {:onClick #((@refs "wdl-editor") :call-method label)
                                                   :style {:color (:text-light style/colors)
                                                           :backgroundColor "white"
                                                           :padding "0 6px"
                                                           :border style/standard-line}}
                                                  (string/capitalize label))
                           [:span {:style {:color (:text-lighter style/colors)
                                           :padding "0 6px"
                                           :border style/standard-line}}
                            (string/capitalize label)]))]
              [:div {:style {:display "flex" :alignItems "baseline" :width "100%"}}
               [:span {:style {:paddingRight "1em"}} "WDL"]
               (links/create-internal {:onClick #(.click (@refs "wdl-uploader"))} "Load from file...")
               (when file-name
                 [:span {}
                  [:span {:style {:padding "0 1em 0 25px"}} (str "Selected: " file-name)]
                  (links/create-internal {:onClick #(this :-set-wdl-text (:file-contents @state))} "Reset to file")])
               [:span {:style {:flex "1 0 auto"}}]
               (link "undo" undo?)
               (link "redo" redo?)]))
           [:style {} ".CodeMirror {height: 300px}"]
           [CodeMirror {:data-test-id "wdl-field"
                        :ref "wdl-editor" :text (:payload info) :read-only? false
                        :initialize (fn [self]
                                      (self :add-listener "change"
                                       #(swap! state assoc :undo-history
                                               (js->clj (self :call-method "historySize")))))}]

           (when (:edit-mode? info)
             [:div {:style {:marginTop "1rem"}}
              [comps/Checkbox {:ref "redact-checkbox"
                               :label (str "Redact Snapshot " (:snapshotId info))}]])

           [comps/ErrorViewer {:error (:upload-error @state)}]
           (style/create-validation-error-message (:validation-errors @state))])
         :ok-button (react/create-element
                     [buttons/Button {:data-test-id "upload-button"
                                    :ref "ok-button"
                                      :text (:ok-text info)
                                      :onClick #(this :-create-method)}])}]))
   :-set-wdl-text
   (fn [{:keys [refs]} text]
     ((@refs "wdl-editor") :call-method "setValue" text))
   :-create-method
   (fn [{:keys [state locals refs this]}]
     (let [[namespace name & fails] (input/get-and-validate refs "namespace" "name")
           [synopsis documentation] (common/get-text refs "synopsis" "documentation")
           wdl ((@refs "wdl-editor") :call-method "getValue")
           fails (or fails (when (string/blank? wdl) ["Please enter the WDL payload"]))]
       (swap! state assoc :validation-errors fails)
       (when-not fails
         (swap! state assoc :banner "Uploading...")
         (if (:edit-mode? (:info @locals))
           (let [{:keys [snapshotId]} (:info @locals)
                 redact? ((@refs "redact-checkbox") :checked?)]
             (endpoints/call-ajax-orch
              {:endpoint (endpoints/create-new-method-snapshot namespace name snapshotId redact?)
               :payload (assoc (utils/restructure synopsis documentation) :payload wdl)
               :headers utils/content-type=json
               :on-done
               (fn [{:keys [success? status-code get-parsed-response]}]
                 (if success?
                   (this :-complete
                         (build-new-entity-id get-parsed-response)
                         (when (= 206 status-code) "Method successfully copied, but error while redacting."))
                   (swap! state assoc :banner nil :upload-error (get-parsed-response false))))}))
           (endpoints/call-ajax-orch
            {:endpoint endpoints/post-method
             :payload (assoc (utils/restructure namespace name synopsis documentation)
                        :payload wdl :entityType "Workflow")
             :headers utils/content-type=json
             :on-done
             (fn [{:keys [success? get-parsed-response]}]
               (if success?
                 (this :-complete (build-new-entity-id get-parsed-response))
                 (swap! state assoc :banner nil :upload-error (get-parsed-response false))))})))))
   :-complete
   (fn [{:keys [props]} new-entity-id & [error-message]]
     ((:dismiss props))
     ((:on-created props) :method new-entity-id)
     (when error-message
       (comps/push-error error-message)))})
