# firecloud-ui

FireCloud user interface for web browsers.

https://firecloud.dsde-dev.broadinstitute.org/

https://firecloud.dsde-staging.broadinstitute.org/

## Technologies

[ClojureScript](https://github.com/clojure/clojurescript) is used for the UI.

We use the [Leiningen](http://leiningen.org/) build tool to manage ClojureScript dependencies.

The code incorporates usage of [react-cljs](https://github.com/dmohs/react-cljs) which is 
a ClojureScript wrapper for [React](https://facebook.github.io/react/).

Figwheel replaces the running JavaScript within the page so changes are visible without a browser reload. More information [here](https://github.com/bhauman/lein-figwheel). [This video](https://www.youtube.com/watch?v=j-kj2qwJa_E) gives some insight into the productivity gains available when using this technology (up to about 15:00 is all that is necessary).

## Getting Started

Start with a [docker](https://www.docker.com/) environment.

You will need to create a Google web application client ID from here:

https://console.developers.google.com/

You can create credentials for yourself in **"APIs & Auth" -> "Credentials."** You want an **OAuth 2.0 client ID** with a **Web application** application type. For more details, see Google's help page:

https://developers.google.com/identity/sign-in/web/devconsole-project

Add your docker host as an authorized JavaScript origin. By convention, we use `dhost` as the hostname, so we add the following origins:
- https://dhost
- http://dhost

The HTTP (vs. HTTPS) origin is necessary for hot-reloading during local development since Figwheel does not support HTTPS.

To use this host, you'll need to add your docker machine's IP address (the address returned by `docker-machine ip default`) to your `/etc/hosts` file, e.g.,
```
192.168.99.100 dhost
```

Set your client ID in your environment:
```
export GOOGLE_CLIENT_ID='...'
```

Start the server in docker:
```
./script/dev/start-server.sh
```

Build the code:

```
./script/dev/build-once.sh
```

(See below for additional build options.)

You should now be able to view the application at `http://dhost/` or `https://dhost/`.

To run against a local instance of the orchestration server (running in docker), try:
```
ORCH_URL_ROOT='http://orch:8080' ./script/dev/start-server.sh
```

## Build Options

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

For this build, you must be viewing the application via HTTP, not HTTPS. HTTPS is not supported by Figwheel.

This can take around 20 seconds to completely start. When ready, it will display the following message:
```
Prompt will show when figwheel connects to your application
```

To connect, reload the browser window. The prompt should appear less than ten seconds after you reload the page. If it is not connecting, make sure to check the JavaScript console for error messages.
