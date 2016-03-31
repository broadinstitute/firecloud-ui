(ns org.broadinstitute.firecloud-ui.config)


(def config (atom nil))


(defn api-url-root [] (get @config "apiUrlRoot"))
(defn debug? [] (get @config "isDebug"))
(defn tcga-namespace [] (get @config "tcgaNamespace"))
