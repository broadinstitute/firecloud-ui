(ns broadfcui.utils)


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
  []
  `(js* "debugger;"))


(defmacro restructure
  "Package bound values into a map with keywords:
   (def x 3)
   (def y 4)
   (restructure x y)
   ; => {:x 3 :y 4}"
  [& symbols]
  (zipmap (map keyword symbols) symbols))


(defmacro multi-swap!
  "Update an atom with multiple functions:
   (def state (atom {:a 1 :b 2}))
   (multi-swap! state (assoc :c 3) (dissoc :b) (update :a inc))
   #_=> {:a 2 :c 3}"
  [a & forms]
  `(swap! ~a (fn [x#] (-> x# ~@forms))))

(defmacro generate-build-timestamp []
  (System/currentTimeMillis))
