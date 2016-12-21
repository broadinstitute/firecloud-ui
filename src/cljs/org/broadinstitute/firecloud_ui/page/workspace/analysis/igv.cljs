(ns org.broadinstitute.firecloud-ui.page.workspace.analysis.igv
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.utils :as utils]
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


(react/defc IGVContainer
  {:render
   (fn [{:keys []}]
     [:div {:ref "container"}])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :refresh this))
   :component-did-update
   (fn [{:keys [props prev-props this]}]
     (when (not= (:tracks props) (:tracks prev-props))
       (react/call :refresh this)))
   :refresh
   (fn [{:keys [props refs]}]
     (.createBrowser js/igv (@refs "container") (options (:tracks props))))})
