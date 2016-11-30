(ns org.broadinstitute.firecloud-ui.page.workspace.summary.library-utils
  (:require
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(defn strip-library-prefix [key]
  (let [[_ _ attr] (re-find #"(.*):(.*)" (name key))]
    (keyword attr)))
