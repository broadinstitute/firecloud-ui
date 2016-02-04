# FireCloud UI

FireCloud user interface for web browsers.

https://firecloud.dsde-prod.broadinstitute.org/

## Technologies

[ClojureScript](https://github.com/clojure/clojurescript) is used for the UI.

We use the [Leiningen](http://leiningen.org/) build tool to manage ClojureScript dependencies.

The code incorporates usage of [react-cljs](https://github.com/dmohs/react-cljs) which is 
a ClojureScript wrapper for [React](https://facebook.github.io/react/).

Figwheel replaces the running JavaScript within the page so changes are visible without a browser reload. More information [here](https://github.com/bhauman/lein-figwheel). [This video](https://www.youtube.com/watch?v=j-kj2qwJa_E) gives some insight into the productivity gains available when using this technology (up to about 15:00 is all that is necessary).

## Getting Started

see https://github.com/broadinstitute/firecloud-environment

## Build Options

Build once:
```
./script/build-once.sh
```

Watch files and rebuild whenever a file is saved:
```
./script/start-auto-build.sh
```

Watch files, rebuild, and reload changes into the running browser window:
```
./script/start-hot-reloader.sh
```

For this build, you must be viewing the application via HTTP, not HTTPS. HTTPS is not supported by Figwheel. Also, set FIGWHEEL_HOST in your environment to point to your docker machine's IP address so the Figwheel client in the browser can connect to the Figwheel server.

This can take around 20 seconds to completely start. When ready, it will display the following message:
```
Prompt will show when figwheel connects to your application
```

To connect, reload the browser window. The prompt should appear less than ten seconds after you reload the page. If it is not connecting, make sure to check the JavaScript console for error messages.
