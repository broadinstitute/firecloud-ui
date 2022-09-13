(ns broadfcui.page.workspace.analysis.igv
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   [broadfcui.components.script-loader :refer [ScriptLoader]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   ))

(defn- options [tracks token]
  (clj->js
   {
    ;; TODO: should we set hg38 as the default?
    :genome "hg19"
    :oauthToken token
    :tracks (map-indexed (fn [index {:keys [track-url index-url]}]
                           (let [track-filename (first (string/split track-url #"[\#\?]")) ;; remove any querystring or hash
                                 file-extension (string/lower-case (last (string/split track-filename ".")))]
                             {:name (str "Track " (inc index))
                              ;; NB: IGV knows how to infer the track type from its url - but this doesn't work for sourceType="gcs"
                              ;; so we will set the track type explicitly.
                              :sourceType "gcs"
                              :type (case file-extension
                                      "bam" "alignment"
                                      "bed" "annotation"
                                      "vcf" "variant")
                              :url track-url
                              :indexURL (when (string? @index-url) @index-url)
                              ;; NB: we'd love to skip setting indexURL and set indexed=true instead, which allows IGV to search
                              ;; for indexes by naming convention, similar (but more lazily-loaded than) our track selector modal.
                              ;; However, IGV's naming convention only supports (filename + ".bai"), e.g. my.bam.bai, whereas
                              ;; sometimes we also want (filename.replace(".bam", ".bai")), e.g. my.bai.
                              ;; So, we have keep our own track selector with index-finder.
                              }))
                         tracks)}))

(react/defc IGVContainer
  {
   :render
   (fn [{:keys [this state]}]
     (let [{:keys [deps-loaded? error?]} @state]
       [:div {}
        [:div {:ref "container" :data-test-id "igv-container"}]
        (cond
          error? (style/create-server-error-message "Unable to load IGV.")
          :else [ScriptLoader
                  {:key "igv"
                   :on-error #(swap! state assoc :error? true)
                   :on-load #(do
                               (swap! state assoc :igv-loaded? true)
                               (this :refresh))
                   :path "https://igv.org/web/release/2.0.1/dist/igv.min.js"}])]))
   :component-did-update
   (fn [{:keys [props state prev-props this]}]
     (when (and (not= (:tracks props) (:tracks prev-props)) (:igv-loaded? @state))
       (this :refresh)))
   :refresh
   (fn [{:keys [props refs]}]
     ;; .createBrowser returns a Promise that we should be using to add/remove tracks. For now, we do it brute-force:
     ;; empty the container, then create a new IGV browser each time we change tracks.
     (set! (.-innerHTML (@refs "container")) "")
     (let [tracks (:tracks props)]
       (if (empty? tracks)
         ;; if the user hasn't specified any tracks, render the IGV shell without getting a pet token
         (.createBrowser js/igv (@refs "container") (options [] ""))
         ;; when the user DOES have tracks, get a token for the user's pet, then pass that token into the IGV tracks.
         (endpoints/call-ajax-sam
           {:endpoint (endpoints/pet-token (get-in props [:workspace-id :namespace]))
            :payload common/storage-scopes
            :headers ajax/content-type=json
            :on-done
              (fn [{:keys [success? raw-response]}]
                (if success?
                  ;; Sam endpoint returns a quoted token; dequote it.
                  (let [pet-token (utils/dequote raw-response)]
                    (.createBrowser js/igv (@refs "container") (options (:tracks props) pet-token)))
                  ;; TODO: better error display
                  (js/alert raw-response)))}))))})
