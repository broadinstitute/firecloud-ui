(ns org.broadinstitute.firecloud-ui.testrunner
  (:require
   cljs.test
   org.broadinstitute.firecloud-ui.configtest
   org.broadinstitute.firecloud-ui.main
   org.broadinstitute.firecloud-ui.testutils
   ))

(defn run-all-tests []
  (cljs.test/run-tests 'org.broadinstitute.firecloud-ui.configtest))
