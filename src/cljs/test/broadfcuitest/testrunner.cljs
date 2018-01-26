(ns broadfcuitest.testrunner
  (:require
   cljs.test
   broadfcui.main
   broadfcuitest.components.buttons
   broadfcuitest.components.research-purpose
   broadfcuitest.config
   broadfcuitest.duration
   broadfcuitest.strip-comment-leaders
   broadfcuitest.style
   broadfcuitest.utils
   ))

(defn ^:export run-all-tests []
  (cljs.test/run-tests 'broadfcuitest.config)
  (cljs.test/run-tests 'broadfcuitest.duration)
  (cljs.test/run-tests 'broadfcuitest.style)
  (cljs.test/run-tests 'broadfcuitest.components.buttons)
  (cljs.test/run-tests 'broadfcuitest.components.research-purpose)
  (cljs.test/run-tests 'broadfcuitest.strip-comment-leaders)
  )
