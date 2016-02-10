(ns org.broadinstitute.firecloud-ui.config)


(def config (atom nil))


(defn debug? []
  (get @config "isDebug"))