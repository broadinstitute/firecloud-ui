(ns broadfcui.page.workspace.analysis.igv
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   [broadfcui.components.script-loader :refer [ScriptLoader]]
   [broadfcui.utils :as utils]
   ))

(defonce ^:private igv-styles-loaded? (atom false))

(defn- load-igv-styles []
  (.. (js/$ "head") ; IGV uses jQuery, so I'm using it here too.
      (append "<link rel=\"stylesheet\" type=\"text/css\" href=\"https://igv.org/web/release/1.0.1/igv-1.0.1.css\">"))
  (reset! igv-styles-loaded? true))

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

(react/defc IGVContainer
  {:component-will-mount
   (fn []
     (when-not @igv-styles-loaded?
       (load-igv-styles)))
   :render
   (fn [{:keys [this state]}]
     (let [{:keys [deps-loaded? error?]} @state]
       [:div {}
        [:div {:ref "container"}]
        (cond
          error? (style/create-server-error-message "Unable to load IGV.")
          deps-loaded? [ScriptLoader
                        {:key "igv"
                         :on-error #(swap! state assoc :error? true)
                         :on-load #(do
                                     (swap! state assoc :igv-loaded? true)
                                     (this :refresh))
                         :path "https://igv.org/web/release/1.0.6/igv-1.0.6.min.js"}]
          :else [ScriptLoader
                 {:key "igv-deps"
                  :on-error #(swap! state assoc :error? true)
                  :on-load #(swap! state assoc :deps-loaded? true)
                  :path "igv-deps.bundle.js"}])]))
   :component-did-update
   (fn [{:keys [props state prev-props this]}]
     (when (and (not= (:tracks props) (:tracks prev-props)) (:igv-loaded? @state))
       (this :refresh)))
   :refresh
   (fn [{:keys [props refs]}]
     (.createBrowser js/igv (@refs "container") (options (:tracks props))))})
