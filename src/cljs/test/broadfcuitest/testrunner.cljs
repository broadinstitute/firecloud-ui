(ns broadfcuitest.testrunner
  (:require
   cljs.test
   broadfcui.main
   broadfcuitest.common.gcs-file-preview
   broadfcuitest.components.buttons
   broadfcuitest.components.research-purpose
   broadfcuitest.config
   broadfcuitest.duration
   broadfcuitest.strip-comment-leaders
   broadfcuitest.sort-order-submission
   broadfcuitest.style
   broadfcuitest.utils
   ))

(defn ^:export run-all-tests []
  (cljs.test/run-tests 'broadfcuitest.config)
  (cljs.test/run-tests 'broadfcuitest.duration)
  (cljs.test/run-tests 'broadfcuitest.style)
  (cljs.test/run-tests 'broadfcuitest.common.gcs-file-preview)
  (cljs.test/run-tests 'broadfcuitest.components.buttons)
  (cljs.test/run-tests 'broadfcuitest.components.research-purpose)
  (cljs.test/run-tests 'broadfcuitest.strip-comment-leaders)
  (cljs.test/run-tests 'broadfcuitest.sort-order-submission)
  )
