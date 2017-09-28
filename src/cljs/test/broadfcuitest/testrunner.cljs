(ns broadfcuitest.testrunner
  (:require
   cljs.test
   broadfcui.main
   broadfcuitest.components.buttons
   broadfcuitest.config
   broadfcuitest.duration
   broadfcuitest.style
   broadfcuitest.utils
   ))

(defn ^:export run-all-tests []
  (cljs.test/run-tests 'broadfcuitest.config)
  (cljs.test/run-tests 'broadfcuitest.duration)
  (cljs.test/run-tests 'broadfcuitest.style)
  (cljs.test/run-tests 'broadfcuitest.components.buttons)
  )
