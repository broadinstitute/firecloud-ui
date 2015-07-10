;; define a root namesapce 'org.broadinstitute'
(def root-ns "org.broadinstitute")

;; set target as the relative build directory
(def build-dir-relative "target")


;; defproject settings for lein
(defproject org.broadinstitute/firecloud-ui "0.0.1"
  :dependencies
  [
   ;; React library and cljs bindings for React.
   [dmohs/react "0.2.5"]
   [org.clojure/clojure "1.6.0"]
   [org.clojure/clojurescript "0.0-3211"]
   ]
  :plugins [[lein-cljsbuild "1.0.5"] [lein-figwheel "0.3.1"]]
  :hooks [leiningen.cljsbuild]
  :profiles {:dev {:cljsbuild
                   {:builds {:client {:compiler
                                      {:optimizations :none
                                       :source-map true
                                       :source-map-timestamp true}
                                      :figwheel
                                      {:on-jsload ~(str root-ns
                                                        ".firecloud-ui.main/dev-reload")}}}}}
             :release {:cljsbuild
                       {:builds {:client {:compiler
                                          {:optimizations :advanced
                                           :pretty-print false
                                           :closure-defines {"goog.DEBUG" false}}}}}}}
  :cljsbuild {:builds {:client {:source-paths ["src/cljs"]
                                :compiler {:output-dir ~(str build-dir-relative "/build")
                                           :output-to ~(str build-dir-relative "/compiled.js")}}}})
