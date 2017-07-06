(ns broadfcui.page.workspace.analysis.igv-utils
  (:require
    [broadfcui.common :as common]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.utils :as utils]
    ))


(defn- finder [{:keys [track-url on-success on-error]} options]
  (let [possible-idxs (atom nil)]
    (add-watch possible-idxs :loop
               (fn [_ _ _ new-list]
                 (if (empty? new-list)
                   (on-error (str "Index file not found for " track-url))
                   (let [possible-idx (first new-list)
                         parsed (common/parse-gcs-uri possible-idx)]
                     (endpoints/call-ajax-orch
                      {:endpoint (endpoints/get-gcs-stats (:bucket-name parsed) (:object parsed))
                       :on-done (fn [{:keys [success?]}]
                                  (if success?
                                    (on-success possible-idx)
                                    (swap! possible-idxs rest)))})))))
    (reset! possible-idxs options)))

(defn- find-bai [{:keys [track-url] :as args}]
  (finder args [(clojure.string/replace track-url #"\.bam$" ".bai")
                (str track-url ".bai")]))

(defn- find-idx [{:keys [track-url] :as args}]
  (finder args [(str track-url ".idx")
                (clojure.string/replace track-url #"\..{3}$" ".idx")]))


(defn find-index-file [{:keys [track-url on-error] :as args}]
  (let [file-type (subs track-url (inc (.lastIndexOf track-url ".")))]
    (condp = (clojure.string/lower-case file-type)
      "bam" (find-bai args)
      "vcf" (find-idx args)
      "bed" (find-idx args)
      (on-error (str "Unknown file type: " file-type)))))
