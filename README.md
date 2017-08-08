# FireCloud UI

FireCloud user interface for web browsers.

https://portal.firecloud.org

## Technologies

[ClojureScript](https://github.com/clojure/clojurescript) is used for the UI.

We use the [Leiningen](http://leiningen.org/) build tool to manage ClojureScript dependencies.

The code incorporates usage of [react-cljs](https://github.com/dmohs/react-cljs) which is a ClojureScript wrapper for [React](https://facebook.github.io/react/).

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

```sh
./script/build.sh compile
```

To compile and build the `broadinstitute/firecloud-ui` docker image, run

```sh
./script/build.sh compile -d build
```

## Selenium tests

Selenium tests are found in the `automation` directory.  They should run against a firecloud-in-a-box (FiaB).

### Running with docker

First build the docker image

```sh
docker build -f Dockerfile-tests -t automation .
```

Then run the run-tests script with the newly created image.  This script will render the `application.conf` and `firecloud-account.pem` from vault to be used by the test container.  Note that if you are running outside of docker you will need to generate these files manually.

```sh
cd automation/docker
./run-tests.sh 4 qa <ip of FiaB> automation $(cat ~/.vault-token)
```

### Running locally
If you have a FiaB running and its IP configured in your `/etc/hosts`, you can run tests locally and watch them execute in a browser.

#### Set Up

First run render script to generate necessary configs:

```sh
cd automation
./render-local-env.sh . $(cat ~/.vault-token)
```


If you have a local UI running, you will need to go into `automation/src/test/resources` and edit the `baseURL` in `application.conf`:

```
baseUrl = "http://local.broadinstitute.org"
```


#### Running tests

To run all tests:

```sh
sbt test -Djsse.enableSNIExtension=false -Dheadless=false
sbt clean
```

To run a single suite:

```sh
sbt -Djsse.enableSNIExtension=false -Dheadless=false "testOnly *GoogleSpec"
sbt clean
```

To run a single test within a suite:

```sh
# matches test via substring
sbt -Djsse.enableSNIExtension=false -Dheadless=false "testOnly *GoogleSpec -- -z \"have a search field\""
sbt clean
```

For more information see: http://www.scala-sbt.org/0.13/docs/Testing.html#Test+Framework+Arguments


