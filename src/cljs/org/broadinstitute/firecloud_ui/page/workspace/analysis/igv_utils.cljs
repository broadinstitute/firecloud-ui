(ns org.broadinstitute.firecloud-ui.page.workspace.analysis.igv-utils
  (:require
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- find-index-bam [{:keys [track-url on-success on-error]}]
  (let [possible-bais (atom nil)]
    (add-watch possible-bais :loop
               (fn [_ _ _ new-list]
                 (if (empty? new-list)
                   (on-error (str "Index file not found for " track-url))
                   (let [possible-bai (first new-list)
                         parsed (common/parse-gcs-uri possible-bai)]
                     (endpoints/call-ajax-orch
                       {:endpoint (endpoints/get-gcs-stats (:bucket-name parsed) (:object parsed))
                        :on-done (fn [{:keys [success?]}]
                                   (if success?
                                     (on-success possible-bai)
                                     (swap! possible-bais rest)))})))))
    (reset! possible-bais [(clojure.string/replace track-url #".bam$" ".bai")
                           (str track-url ".bai")])))


(defn find-index [{:keys [track-url on-error] :as args}]
  (let [file-type (subs track-url (inc (.lastIndexOf track-url ".")))]
    (condp = (clojure.string/lower-case file-type)
      "bam" (find-index-bam args)
      (on-error (str "Unknown file type: " file-type)))))
