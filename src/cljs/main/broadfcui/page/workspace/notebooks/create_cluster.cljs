(ns broadfcui.page.workspace.notebooks.create-cluster
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.input :as input]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.components.foundation-tooltip :refer [FoundationTooltip]]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.notebooks.utils :as notebook-utils]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   ))

(def machineTypes ["n1-standard-1"
                   "n1-standard-2"
                   "n1-standard-4"
                   "n1-standard-8"
                   "n1-standard-16"
                   "n1-standard-32"
                   "n1-standard-64"
                   "n1-standard-96"
                   "n1-highcpu-4"
                   "n1-highcpu-8"
                   "n1-highcpu-16"
                   "n1-highcpu-32"
                   "n1-highcpu-64"
                   "n1-highcpu-96"
                   "n1-highmem-2"
                   "n1-highmem-4"
                   "n1-highmem-8"
                   "n1-highmem-16"
                   "n1-highmem-32"
                   "n1-highmem-64"
                   "n1-highmem-96"])

(react/defc ClusterCreator
  {:refresh
   (fn [])
   :get-initial-state
   (fn []
     {:labels []
      :label-gensym (gensym)})
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [creating? server-error validation-errors labels label-gensym]} @state
           {:keys [choose-notebook dismiss]} props]
       [modals/OKCancelForm
        {:header "Create Cluster"
         :dismiss dismiss
         :ok-button {:onClick #(this :-create-cluster)}
         :content
         (react/create-element
          [:div {:style {:marginBottom -20}}
           (when creating? (blocker "Creating cluster..."))
           (notebook-utils/create-inline-form-label
            (str "Create a cluster to associate with notebook \"" (notebook-utils/notebook-name choose-notebook) "\":"))
           (react/create-element
            [:div {:style {:marginTop 25}}
             (style/create-form-label "Name")
             [input/TextField {:data-test-id "cluster-name-input" :ref "clusterNameCreate" :autoFocus true :style {:width "100%"}
                               :defaultValue "" :predicates [(input/nonempty "Cluster name") (input/alphanumeric_- "Cluster name")]}]
             [Collapse
              {:data-test-id "optional-settings"
               :style {:marginLeft -20} :default-hidden? true
               :title [:span {:style {:marginBottom 20 :fontStyle "italic"}} "Optional Settings..."]
               :contents
               (react/create-element
                [:div {}
                 (flex/box {}
                   [:div {:style {:width "48%" :marginRight "4%" :marginBottom "1%"}}
                    [FoundationTooltip {:text (notebook-utils/create-inline-form-label "Master Machine Type")
                                        :tooltip "Determines the number of CPUs and memory for the master VM."}]]
                   [:div {:style {:width "48%" :marginBottom "1%"}}
                    [FoundationTooltip {:text (notebook-utils/create-inline-form-label "Master Disk Size")
                                        :tooltip "Size of the disk on the master VM. Minimum size is 100GB."}]])
                 [:div {:display "inline-block"}
                  (style/create-identity-select {:data-test-id "master-machine-type-select" :ref "masterMachineType"
                                                 :style {:width "48%" :marginRight "4%"} :defaultValue "n1-standard-4"}
                    machineTypes)
                  [input/TextField {:data-test-id "master-disk-size-input" :ref "masterDiskSize" :autoFocus true
                                    :style {:width "41%"} :defaultValue 500 :min 0 :type "number"}]
                  [:span {:style {:marginLeft "2%"}} (notebook-utils/create-inline-form-label "GB")]]
                 [:div {:style {:marginBottom "1%"}}
                  [FoundationTooltip {:text (style/create-form-label "Workers")
                                      :tooltip "Workers can be 0, 2 or more. Google Dataproc does not allow 1 worker."}]]
                 [input/TextField {:data-test-id "workers-input" :ref "numberOfWorkers" :autoFocus true
                                   :style {:width "100%"} :defaultValue 0 :min 0 :type "number"}]
                 (flex/box {}
                   [:div {:style {:width "48%" :marginRight "4%" :marginBottom "1%"}}
                    [FoundationTooltip {:text (style/create-form-label "Worker Local SSDs")
                                        :tooltip "The number of local solid state disks for workers. Ignored if Workers is 0."}]]
                   [:div {:style {:width "48%" :marginBottom "1%"}}
                    [FoundationTooltip {:text (style/create-form-label "Preemptible Workers")
                                        :tooltip "Ignored if Workers is 0."}]])
                 (flex/box {}
                   [input/TextField {:data-test-id "worker-local-ssds-input" :ref "numberOfWorkerLocalSSDs" :autoFocus true
                                     :style {:width "48%" :marginRight "4%"} :defaultValue 0 :min 0 :type "number"}]
                   [input/TextField {:data-test-id "preemptible-workers-input" :ref "numberOfPreemptibleWorkers"
                                     :autoFocus true :style {:width "48%"} :defaultValue 0 :min 0 :type "number"}])
                 (flex/box {}
                   [:div {:style {:width "48%" :marginRight "4%" :marginBottom "1%"}}
                    [FoundationTooltip {:text (notebook-utils/create-inline-form-label "Worker Machine Type")
                                        :tooltip "Determines the number of CPUs and memory for each worker VM. Ignored if Workers is 0."}]]
                   [:div {:style {:width "48%" :marginBottom "1%"}}
                    [FoundationTooltip {:text (notebook-utils/create-inline-form-label "Worker Disk Size")
                                        :tooltip "Size of the disk on each worker VM. Minimum size is 100GB. Ignored if Workers is 0."}]])
                 [:div {:display "inline-block"}
                  (style/create-identity-select {:data-test-id "worker-machine-type-select" :ref "workerMachineType"
                                                 :style {:width "48%" :marginRight "4%"} :defaultValue "n1-standard-4"}
                    machineTypes)
                  [input/TextField {:data-test-id "worker-disk-size-input" :ref "workerDiskSize" :autoFocus true
                                    :style {:width "41%"} :defaultValue 500 :min 0 :type "number"}]
                  [:span {:style {:marginLeft "2%"}} (notebook-utils/create-inline-form-label "GB")]]

                 [:div {:style {:marginBottom "1%"}}
                  [FoundationTooltip {:text (style/create-form-label "Extension URI")
                                      :tooltip "The GCS URI of an archive containing Jupyter notebook extension files.
                                   The archive must be in tar.gz format, must not include a parent directory,
                                   and must have an entry point named 'main'."}]]
                 [input/TextField {:data-test-id "extension-uri-input" :ref "extensionURI" :autoFocus true :style {:width "100%"}}]
                 [:div {:style {:marginBottom "1%"}}
                  [FoundationTooltip {:text (style/create-form-label "Custom Script URI")
                                      :tooltip "The GCS URI of a bash script you wish to run on your cluster before it starts up."}]]
                 [input/TextField {:data-test-id "custom-script-uri-input" :ref "userScriptURI" :autoFocus true :style {:width "100%"}}]
                 (when (seq labels)
                   [:div {:key label-gensym}
                    (flex/box {}
                      [:span {:style {:width "50%"}} (notebook-utils/create-inline-form-label "Key")]
                      [:span {:style {:width "50%" :marginLeft "4%"}} (notebook-utils/create-inline-form-label "Value")])
                    (map-indexed (fn [i label]
                                   (flex/box {:style {:marginBottom 10}}
                                     (links/create-internal
                                       {:style {:color (:text-light style/colors)
                                                :marginLeft -20
                                                :minHeight 20 :minWidth 20
                                                }
                                        :href "javascript:;"
                                        :onClick #(utils/multi-swap! state (assoc :label-gensym (gensym)) (update :labels utils/delete i))}
                                       (icons/render-icon {:style {:marginTop "35%"}} :remove))
                                     [input/TextField {:data-test-id (str "key-" i "-input")
                                                       :style {:ref (str "key" i) :marginBottom 0 :width "48%" :marginRight "4%"}
                                                       :defaultValue (first label)
                                                       :onChange #(swap! state update-in [:labels i]
                                                                         assoc 0 (-> % .-target .-value))}]
                                     [input/TextField {:data-test-id (str "value-" i "-input")
                                                       :style {:ref (str "val" i) :marginBottom 0 :width "48%"}
                                                       :defaultValue (last label)
                                                       :onChange #(swap! state update-in [:labels i]
                                                                         assoc 1 (-> % .-target .-value))}]))
                                 labels)])
                 [buttons/Button {:text "Add Label" :icon :add-new :style {:marginBottom 10} :data-test-id "add-label-button"
                                  :onClick #(utils/multi-swap! state (update :labels conj ["" ""]) (assoc :label-gensym (gensym)))}]])}]])
           [comps/ErrorViewer {:error server-error}]
           (style/create-validation-error-message validation-errors)])}]))

   :-create-cluster
   (fn [{:keys [this state refs props]}]
     (swap! state dissoc :server-error :validation-errors)
     (let [[clusterNameCreate extensionURI userScriptURI & fails] (input/get-and-validate refs "clusterNameCreate" "extensionURI" "userScriptURI")
           payload {:labels (this :-process-labels)}
           machineConfig (this :-process-machine-config)
           {:keys [reload-after-choose dismiss]} props]
       (if fails
         (swap! state assoc :validation-errors fails)
         (do (swap! state assoc :creating? true)
             (endpoints/call-ajax-leo
              {:endpoint (endpoints/create-cluster (get-in props [:workspace-id :namespace]) clusterNameCreate)
               :payload (merge payload
                               {:machineConfig machineConfig}
                               (when-not (string/blank? extensionURI) {:jupyterExtensionUri extensionURI})
                               (when-not (string/blank? userScriptURI) {:jupyterUserScriptUri userScriptURI}))
               :headers ajax/content-type=json
               :on-done (fn [{:keys [success? get-parsed-response]}]
                          (swap! state dissoc :creating?)
                          (if success?
                            (do
                              (reload-after-choose clusterNameCreate)
                              (dismiss))
                            (swap! state assoc :server-error (get-parsed-response false))))})))))
   :-process-labels
   (fn [{:keys [state]}]
     (let [labelsEmptyRemoved (filter #(not= % ["" ""]) (:labels @state))]
       (zipmap (map (comp keyword first) labelsEmptyRemoved)
               (map last labelsEmptyRemoved))))

   :-process-machine-config
   (fn [{:keys [refs]}]
     (let [getInt #(if (string/blank? %) % (js/parseInt %))
           machineConfig {:numberOfWorkers (getInt (input/get-text refs "numberOfWorkers"))
                          :masterMachineType (.-value (@refs "masterMachineType"))
                          :masterDiskSize (getInt (input/get-text refs "masterDiskSize"))
                          :workerMachineType (.-value (@refs "workerMachineType"))
                          :workerDiskSize (getInt (input/get-text refs "workerDiskSize"))
                          :numberOfWorkerLocalSSDs (getInt (input/get-text refs "numberOfWorkerLocalSSDs"))
                          :numberOfPreemptibleWorkers (getInt (input/get-text refs "numberOfPreemptibleWorkers"))}]
       (apply dissoc
              machineConfig
              (for [[k v] machineConfig :when (or (nil? v) (string/blank? v))] k))))})
