(defproject org.broadinstitute/cloud-pilot "0.0.1"
  :dependencies
  [
   ;; React library and cljs bindings for React.
   [dmohs/react "0.2.3"]
   [org.clojure/clojure "1.6.0"]
   [org.clojure/clojurescript "0.0-3211"]
   ]
  :plugins [[lein-cljsbuild "1.0.5"]]
  :hooks [leiningen.cljsbuild]
  :profiles {:dev {:cljsbuild
                   {:builds {:client {:compiler
                                      {:optimizations :none
                                       :source-map true}}}}}
             :release {:cljsbuild
                       {:builds {:client {:compiler
                                          {:optimizations :simple
                                           :pretty-print false}}}}}}
  :cljsbuild {:builds {:client {:source-paths ["src/cljs"]}}})
