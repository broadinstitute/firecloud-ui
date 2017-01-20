(ns org.broadinstitute.firecloud-ui.config)


(def config (atom nil))


(defn api-url-root [] (get @config "apiUrlRoot"))
(defn debug? [] (get @config "isDebug"))
(defn google-client-id [] (get @config "googleClientId"))
(defn tcga-namespace [] (get @config "tcgaNamespace"))
(defn workflow-count-warning-threshold [] (get @config "workflowCountWarningThreshold" 100))
(defn submission-status-refresh [] (get @config "submissionStatusRefresh" 60000)) ;; milliseconds
(defn cromwell-version [] (get @config "cromwellVersion" "n/a"))
(defn user-guide-url [] (get @config "userGuideUrl"))
