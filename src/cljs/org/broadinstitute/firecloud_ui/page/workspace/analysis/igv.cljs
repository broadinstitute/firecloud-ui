(ns org.broadinstitute.firecloud-ui.page.workspace.analysis.igv
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- options [tracks]
  (clj->js
    {:genome "hg19"
     :trackDefaults {:palette ["#00A0B0" "#6A4A3C" "#CC333F" "#EB6841"]
                     :bam {:coverageThreshold 0.2
                           :coverageQualityWeight true}}
     :tracks (map-indexed (fn [index track]
                            {:name (str "Track " (inc index))
                             :url track
                             ;; TODO: is this the right relationship between .bam and .bai?
                             :indexURL (clojure.string/replace track #".bam$" ".bai")
                             :type "bam"
                             :sourceType "gcs"
                             :displayMode "EXPANDED"})
                          tracks)}))


(react/defc IGVContainer
  {:render
   (fn [{:keys []}]
     [:div {:ref "div"}])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :refresh this))
   :component-did-update
   (fn [{:keys [this]}]
     (react/call :refresh this))
   :refresh
   (fn [{:keys [props refs locals]}]
     (swap! locals assoc :browser (.createBrowser js/igv (@refs "div") (options (:tracks props)))))})
