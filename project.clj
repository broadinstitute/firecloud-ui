(defn- with-ns [n] (str "org.broadinstitute.firecloud-ui." n))

(defproject org.broadinstitute/firecloud-ui "0.0.1"
  :dependencies
  [
   [dmohs/react "1.0.2+15.0.2"]
   [org.clojure/clojure "1.8.0"]
   [org.clojure/clojurescript "1.9.293"]
   [inflections "0.9.14"]
   [cljsjs/moment "2.9.0-3"]
   [cljsjs/codemirror "5.10.0-0"]
   [cljsjs/marked "0.3.5-0"]
   ]
  :plugins [[lein-cljsbuild "1.1.4"] [lein-figwheel "0.5.8"] [lein-resource "16.9.1"]]
  :profiles {:dev
             {:dependencies [[binaryage/devtools "0.8.3"]]
              :target-path "resources/public"
              :clean-targets ^{:protect false} ["resources/public"]
              :cljsbuild
              {:builds
               {:client
                {:figwheel true
                 :compiler
                 {:optimizations :none
                  :output-dir "resources/public/build"
                  :asset-path "build"
                  :output-to "resources/public/compiled.js"
                  :source-map true
                  :source-map-timestamp true
                  :preloads [devtools.preload]
                  :external-config {:devtools/config {:features-to-install
                                                      [:formatters :hints]}}}}}}}
             :deploy {:cljsbuild
                      {:builds {:client {:compiler
                                         {;; As of 10/29/15, advanced optimization triggers
                                          ;; infinite recursion, which I was not able to figure
                                          ;; out.
                                          :optimizations :simple
                                          :pretty-print false}}}}}}
  :cljsbuild {:builds {:client {:source-paths ["src/cljs"] :compiler {:main ~(with-ns "main")}}}}
  :resource {:resource-paths ["src/static"]
             :excludes [#".*\.DS_Store"]
             :skip-stencil [#"src/static/assets/.*"]
             :extra-values {:vtag ~(.getTime (java.util.Date.))}})
