(ns broadfcuitest.testrunner
  (:require
   cljs.test
   broadfcui.main
   broadfcuitest.config
   broadfcuitest.utils
   ))

(defn run-all-tests []
  (cljs.test/run-tests 'broadfcuitest.config))
