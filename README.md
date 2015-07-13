# firecloud-ui

FireCloud user interface for web browsers.

https://firecloud-ci.broadinstitute.org/

## Getting Started

[ClojureScript](https://github.com/clojure/clojurescript) is used for the UI.
We use the Leiningen build tool. To install, follow the installation instructions on the [Leiningen web site](http://leiningen.org/).
The code incorporates usage of [react-cljs](https://github.com/dmohs/react-cljs) which is 
a ClojureScript wrapper for [React](https://facebook.github.io/react/docs/top-level-api.html)

## Building

Build once:
```
./script/dev/build-once.sh
```

Watch files and rebuild whenever a file is saved:
```
./script/dev/start-auto-build.sh
```

Watch files, rebuild, and reload changes into the running browser window:
```
./script/dev/start-hot-reloader.sh
```

[figwheel](https://github.com/bhauman/lein-figwheel) is used to accomplish this.

This can take around 20 seconds to completely start. When ready, it will display the following message:
```
Prompt will show when figwheel connects to your application
```

To connect, reload the browser window (see the Running section below).

## Running

Start a static file server:
```
./script/dev/serve-locally.py
```

This will serve files at http://localhost:8000/. However, Google Sign-In expects an origin of
http://local.broadinstitute.org:8000/. To make your local instance available at this URL, add the following to
/etc/hosts:
```
127.0.0.1 local.broadinstitute.org
```

## Testing

Run the Leiningen task to run tests:
```
lein test
```

## Create a Release

Build in release mode and remove unnecessary build artifacts:
```
./script/release/build-release.sh
```

At this point, the `target` directory represents the root directory that should be served from a static web server.
