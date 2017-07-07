# FireCloud UI

FireCloud user interface for web browsers.

https://portal.firecloud.org

## Technologies

[ClojureScript](https://github.com/clojure/clojurescript) is used for the UI.

We use the [Leiningen](http://leiningen.org/) build tool to manage ClojureScript dependencies.

The code incorporates usage of [react-cljs](https://github.com/dmohs/react-cljs) which is
a ClojureScript wrapper for [React](https://facebook.github.io/react/).

Figwheel replaces the running JavaScript within the page so changes are visible without a browser reload. More information [here](https://github.com/bhauman/lein-figwheel). [This video](https://www.youtube.com/watch?v=j-kj2qwJa_E) gives some insight into the productivity gains available when using this technology (up to about 15:00 is all that is necessary).

## Getting started

FireCloud is currently supported within Broad's engineering environment:

https://github.com/broadinstitute/firecloud-develop

Support for running FireCloud outside of the Broad is planned.

## Starting the development server

```bash
./config/docker-rsync-local-ui.sh
```

You must be viewing the application via HTTP, not HTTPS. HTTPS is not supported by Figwheel.

This can take around 20 seconds to completely start. When ready, it will display the following message:
```
Prompt will show when figwheel connects to your application
```

To connect, reload the browser window. The prompt should appear less than ten seconds after you reload the page. If it is not connecting, make sure to check the JavaScript console for error messages.

## Testing

To run all tests, enter this at the JavaScript console:

```javascript
broadfcuitest.testrunner.run_all_tests()
```

Check the JavaScript console for test output.

To run a single test, add the following to the bottom of the file you wish to test:

```clojure
(.clear js/console) ; optional
(cljs.test/run-tests)
```

Now, each time that file is saved, Figwheel will reload it and the tests will automatically run.

## Building

To compile the clojure project into the target directory, run 
```
./script/build.sh compile
```

To compile and build the `broadinstitute/firecloud-ui` docker image, run
```
./script/build.sh compile -d build
```

## Selenium tests

Selenium tests are found in the `automation` directory.  They should run against a firecloud-in-a-box (FiaB).

### Running with docker

First build the docker image
```
docker build -f Dockerfile-tests -t automation .
```

Then run the run-tests script with the newly created image.  This script will render the `application.conf` and `firecloud-account.pem` from vault to be used by the test container.  Note that if you are running outside of docker you will need to generate these files manually.
```
cd automation/docker
./run-tests.sh 4 qa <ip of FiaB>
```

### Running locally
Note that you will need to render `automation/docker/application.conf.ctmpl` and copy it to `automation/src/resources/`, as well as `automation/src/firecloud-account.pem.ctmpl`, which should be saved in `/etc`.
Your local `/etc/hosts` file will need to be configured so that Firecloud DNS names are pointing to the IP of your running FiaB.  For more 
```
sbt test -Djsse.enableSNIExtension=false -Dheadless=false
sbt clean
```