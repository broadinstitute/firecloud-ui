(ns org.broadinstitute.firecloud-ui.utils)


(defmacro log-with-transform [transform & args]
  `(let [last-arg# ~(last args)]
     (js/console.log ~@(map (fn [x] `(~transform ~x)) (butlast args)) (~transform last-arg#))
     last-arg#))


(defmacro log
  "Synopsis: (log \"body:\" (.-body js/document))
   Logs arguments without transformation. Returns last argument."
  [& args]
  `(log-with-transform identity ~@args))


(defmacro jslog
  "Synopsis: (jslog \"stuff:\" {:a 1 :b 2})
   Logs, converting to JavaScript objects. Returns last argument."
  [& args]
  `(log-with-transform cljs.core/clj->js ~@args))


(defmacro cljslog
  "Synopsis: (utils/cljslog \"end:\" (assoc (utils/cljslog \"start:\" {:b 3}) :a 4))
   Logs, pretty printing ClojureScript arguments. Returns last argument."
  [& args]
  `(log-with-transform #(with-out-str (cljs.pprint/pprint %)) ~@args))


(defmacro pause
  "JS debugger statement."
  `(js* "debugger;"))
