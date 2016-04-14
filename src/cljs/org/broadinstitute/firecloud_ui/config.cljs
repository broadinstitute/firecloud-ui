(ns org.broadinstitute.firecloud-ui.config)


(def config (atom nil))


(defn sign-in-allowed-access-token-listener-host []
  (get-in @config ["signIn" "allowedAccessTokenListenerHost"]))
(defn api-url-root [] (get @config "apiUrlRoot"))
(defn sign-in-callback-url [] (get-in @config ["signIn" "callbackUrl"]))
(defn debug? [] (get @config "isDebug"))
(defn tcga-namespace [] (get @config "tcgaNamespace"))
