(ns org.broadinstitute.firecloud-ui.testutils
  (:require
   cljs.test
   figwheel.client.utils
   ))

;; cljs.test gets mad if *print-fn* is undefined.
(enable-console-print!)

(defonce ^:private figwheel-log-fn figwheel.client.utils/log)

(defn suppress-figwheel-log []
  (set! figwheel.client.utils/log identity))

(defn restore-figwheel-log []
  (set! figwheel.client.utils/log figwheel-log-fn))

(defn- get-testing-contexts []
  (when (seq (:testing-contexts (cljs.test/get-current-env)))
    (cljs.test/testing-contexts-str)))

;; The following method overrides significantly improve the display of test output in the
;; JavaScript console.

(defmethod cljs.test/report [:cljs.test/default :begin-test-ns] [m]
  (.group js/console (name (:ns m))))

(defmethod cljs.test/report [:cljs.test/default :end-test-ns] [m]
  (.groupEnd js/console))

(defmethod cljs.test/report [:cljs.test/default :begin-test-var] [m]
  (let [env (cljs.test/get-current-env)]
    (.group
     js/console
     (clojure.string/join "." (reverse (map #(:name (meta %)) (:testing-vars env)))))))

(defmethod cljs.test/report [:cljs.test/default :end-test-var] [m]
  (.groupEnd js/console))

(defmethod cljs.test/report [:cljs.test/default :pass] [m]
  (cljs.test/inc-report-counter! :pass)
  (print (get-testing-contexts)))

(defmethod cljs.test/report [:cljs.test/default :fail] [m]
  (cljs.test/inc-report-counter! :fail)
  (.group js/console (get-testing-contexts))
  (when-let [message (:message m)] (print message))
  (cljs.test/print-comparison m)
  (.groupEnd js/console))

(defmethod cljs.test/report [:cljs.test/default :error] [m]
  (cljs.test/inc-report-counter! :error)
  (.error js/console (:actual m)))
