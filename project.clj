(def root-ns "org.broadinstitute")
(def build-dir-relative "target")
(def server-name (or (System/getenv "SERVER_NAME")
                     (throw (Exception. "SERVER_NAME is not defined"))))


(defproject org.broadinstitute/firecloud-ui "0.0.1"
  :dependencies
  [
   ;; React library and cljs bindings for React.
   [dmohs/react "0.2.8"]
   [org.clojure/clojure "1.7.0"]
   [org.clojure/clojurescript "1.7.48"]
   [inflections "0.9.14"]
   [cljsjs/moment "2.9.0-3"]
   ]
  :plugins [[lein-cljsbuild "1.0.6"] [lein-figwheel "0.3.7"]]
  :hooks [leiningen.cljsbuild]
  :profiles {:dev {:cljsbuild
                   {:builds {:client {:compiler
                                      {:optimizations :none
                                       :source-map true
                                       :source-map-timestamp true}
                                      :figwheel
                                      {:on-jsload ~(str root-ns
                                                        ".firecloud-ui.main/dev-reload")
                                       :websocket-url ~(str "ws://" server-name
                                                            ":3449/figwheel-ws")}}}}}
             :minimized {:cljsbuild
                         {:builds {:client {:compiler
                                            {:optimizations :advanced
                                             :pretty-print false
                                             :closure-defines {"goog.DEBUG" false}}}}}}}
  :cljsbuild {:builds {:client {:source-paths ["src/cljs"]
                                :compiler {:output-dir ~(str build-dir-relative "/build")
                                           :output-to ~(str build-dir-relative "/compiled.js")}}}})
