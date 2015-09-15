# firecloud-ui

FireCloud user interface for web browsers.

https://firecloud.dsde-dev.broadinstitute.org/

https://firecloud.dsde-staging.broadinstitute.org/

## Getting Started

This project runs in a Docker container. Docker is available here:

https://www.docker.com/

[ClojureScript](https://github.com/clojure/clojurescript) is used for the UI.
We use the [Leiningen](http://leiningen.org/) build tool.
The code incorporates usage of [react-cljs](https://github.com/dmohs/react-cljs) which is 
a ClojureScript wrapper for [React](https://facebook.github.io/react/).

For authentication, you will need to create a Google web application client ID from here:

https://console.developers.google.com/

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

[figwheel](https://github.com/bhauman/lein-figwheel) is used to accomplish this. [This video](https://www.youtube.com/watch?v=j-kj2qwJa_E) gives some insight into the productivity gains available when using this technology (up to about 15:00 is all that is necessary).

This can take around 20 seconds to completely start. When ready, it will display the following message:
```
Prompt will show when figwheel connects to your application
```

To connect, reload the browser window (see the Running section below).

## Running

Start the static file server and proxy to the service API:
```
GOOGLE_CLIENT_ID=replace-me \
ORCH_URL_ROOT=http://replace-me \
  ./script/dev/start-server.sh
```

## Testing

Run the Leiningen task to run tests:
```
lein test
```

## Production Deployment

Build the docker container:
```
docker build -t firecloud-ui .
```

Run the container:
```
docker run --name firecloud-ui -p 80:80 -p 443:443 \
  -e GOOGLE_CLIENT_ID='replace-me' -e ORCH_URL_ROOT='http://replace-me' \
  firecloud-ui
```
