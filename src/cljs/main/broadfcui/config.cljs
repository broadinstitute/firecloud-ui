(ns broadfcui.config
  (:require
   clojure.set
   clojure.string
   ))

(defn- non-empty-string?
  [s]
  (when (string? s)
    (not (empty? (clojure.string/trim s)))))

(def validators
  {:boolean {:message "must be a boolean" :check boolean?}
   :integer {:message "must be an integer" :check integer?}
   :string {:message "must be a non-empty string" :check non-empty-string?}})

(defn check-config [config]
  (let [config-keys (set (keys config))
        required {"apiUrlRoot" :string "googleClientId" :string "tcgaNamespace" :string}
        optional {"isDebug" :boolean "shibbolethUrlRoot" :string
                  "submissionStatusRefresh" :integer "userGuideUrl" :string "alertsJsonUrl" :string
                  "workflowCountWarningThreshold" :integer "billingGuideUrl" :string "dbGapAuthorizationDomain" :string
                  "callCachingGuideUrl" :string "alertsPollInterval" :integer "forumUrl" :string "authDomainGuideUrl" :string}
        all (merge required optional)
        missing-required (filter #(not (contains? config-keys %)) (keys required))
        extra (clojure.set/difference config-keys (set (keys all)))
        invalid (filter (fn [k]
                          (let [validator (get all k)
                                check (get-in validators [validator :check])]
                            (when-not (check (get config k))
                              k)))
                        (clojure.set/intersection config-keys (set (keys all))))]
    [(not (or (seq missing-required) (seq extra) (seq invalid)))
     (concat
      (map #(str "missing required key " %) missing-required)
      (map #(str "value for " % " " (:message (get validators (get all %)))) invalid)
      (map #(str "unexpected key " %) extra))]))

(def config (atom nil))

(defn api-url-root [] (get @config "apiUrlRoot"))
(defn debug? [] (get @config "isDebug"))
(defn google-client-id [] (get @config "googleClientId"))
(defn tcga-namespace [] (get @config "tcgaNamespace"))
(defn workflow-count-warning-threshold [] (get @config "workflowCountWarningThreshold" 100))
(defn submission-status-refresh [] (get @config "submissionStatusRefresh" 60000)) ;; milliseconds
(defn status-alerts-refresh [] (get @config "alertsPollInterval" 60000)) ;; milliseconds
(defn max-retry-attempts [] (get @config "maxRetryAttempts" 6)) ;; 6 exponential retries = ~ 2 minutes
(defn user-guide-url [] (get @config "userGuideUrl"))
(defn forum-url [] (get @config "forumUrl"))
(defn billing-guide-url [] (get @config "billingGuideUrl"))
(defn call-caching-guide-url [] (get @config "callCachingGuideUrl"))
(defn dbgap-authorization-domain [] (get @config "dbGapAuthorizationDomain"))
(defn alerts-json-url [] (get @config "alertsJsonUrl"))
