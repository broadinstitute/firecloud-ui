(ns broadfcui.page.workspace.analysis.igv
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   [broadfcui.components.script-loader :refer [ScriptLoader]]
   [broadfcui.utils :as utils]
   ))


(defn- options [tracks]
  (clj->js
   {:genome "hg19"
    :trackDefaults {:palette ["#00A0B0" "#6A4A3C" "#CC333F" "#EB6841"]
                    :bam {:coverageThreshold 0.2
                          :coverageQualityWeight true}}
    :tracks (map-indexed (fn [index {:keys [track-url index-url]}]
                           (let [bam? (.endsWith track-url ".bam")]
                             {:name (str "Track " (inc index))
                              :url (common/gcs-uri->google-url track-url)
                              :indexURL (when (string? @index-url) (common/gcs-uri->google-url @index-url))
                              :headers {:Authorization (str "Bearer " (utils/get-access-token))}
                              :displayMode "EXPANDED"
                              :height (when bam? 200)
                              :autoHeight (when bam? false)}))
                         tracks)}))

(react/defc- IGVElement
  {:render
   (fn [{:keys []}]
     [:div {:ref "container"}])
   :component-did-mount
   (fn [{:keys [this]}]
     (this :refresh))
   :component-did-update
   (fn [{:keys [props prev-props this]}]
     (when (not= (:tracks props) (:tracks prev-props))
       (this :refresh)))
   :refresh
   (fn [{:keys [props refs]}]
     (.createBrowser js/igv (@refs "container") (options (:tracks props))))})

(react/defc IGVContainer
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [deps-loaded? igv-loaded? error?]} @state]
       [:div {}
        [:link {:rel "stylesheet" :type "text/css" :href "https://igv.org/web/release/1.0.6/igv-1.0.6.css"}]
        (cond
          error? (style/create-server-error-message "Unable to load IGV.")
          igv-loaded? [IGVElement props]
          deps-loaded? [ScriptLoader
                        {:key "igv"
                         :on-error #(swap! state assoc :error? true)
                         :on-load #(swap! state assoc :igv-loaded? true)
                         :path "https://igv.org/web/release/1.0.6/igv-1.0.6.min.js"}]
          :else [ScriptLoader
                 {:key "igv-deps"
                  :on-error #(swap! state assoc :error? true)
                  :on-load #(swap! state assoc :deps-loaded? true)
                  :path "igv-deps.bundle.js"}])]))})
