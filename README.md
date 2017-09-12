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

```bash
./script/build.sh compile
```

To compile and build the `broadinstitute/firecloud-ui` docker image, run

```bash
./script/build.sh compile -d build
```

## Selenium tests

Selenium tests are found in the `automation` directory.  They should run against a firecloud-in-a-box (FiaB).

### Running with docker

First build the docker image

```bash
cd automation
docker build -f Dockerfile-tests -t automation .
```

Make sure your docker has enough memory allocated (go to Docker->Preferences->Advanced and set the memory to 4 GB). Then run the run-tests script with the newly created image. This script will render the `application.conf` and `firecloud-account.pem` from vault to be used by the test container.  Note that if you are running outside of docker you will need to generate these files manually.

```bash
cd automation/docker
./run-tests.sh 4 qa <ip of FiaB> automation $(cat ~/.vault-token)
```

### Running locally

If you have a FiaB running and its IP configured in your `/etc/hosts`, you can run tests locally and watch them execute in a browser.

#### Set Up

First run render script to generate necessary configs:

```bash
cd automation
./render-local-env.sh $PWD $(cat ~/.vault-token) <"qa" or "dev">
```


If you have a local UI running, you will need to go into `automation/src/test/resources` and edit the `baseURL` in `application.conf`:

```
baseUrl = "http://local.broadinstitute.org/"
```


When starting your UI, run:

```bash
FIAB=true ENV=qa ./config/docker-rsync-local-ui.sh
```


#### Running tests

##### From IntelliJ

Edit your run config defaults for ScalaTest.
Add this to the VM parameters: `-Djsse.enableSNIExtension=false  -Dheadless=false`

Also make sure that there is a `Build` task configured to run before launch.

##### From the command line

To run all tests:

```bash
sbt test -Djsse.enableSNIExtension=false -Dheadless=false
sbt clean
```

To run a single suite:

```bash
sbt -Djsse.enableSNIExtension=false -Dheadless=false "testOnly *GoogleSpec"
sbt clean
```

To run a single test within a suite:

```bash
# matches test via substring
sbt -Djsse.enableSNIExtension=false -Dheadless=false "testOnly *GoogleSpec -- -z \"have a search field\""
sbt clean
```

For more information see: http://www.scala-sbt.org/0.13/docs/Testing.html#Test+Framework+Arguments


### IntelliJ

There are effectively two projects in this repository: a ClojureScript project for the
FireCloud UI and a Scala project for the Selenium-based UI tests. They are logically
linked because the tests rely on the rendered DOM structure (primarily `data-test-id`
attributes) but are otherwise largely separate. Therefore (also in part because of
[https://youtrack.jetbrains.com/issue/SCL-12358]()) we recommend opening
separate IntelliJ projects for each.

For the ClojureScript project, choose `File` -> `Open` and select the base `firecloud-ui`
directory of your git clone. If prompted for a Project JDK, select a recent Java SDK to
use. Assuming you have the Cursive plugin installed, IntelliJ will automatically detect
the `project.clj` file and configure the CLJS Leiningen project.

For the Scala project, choose `File` -> `Open` and select the `automation` directory
inside the git clone. Select a recent Java SDK (i.e. 1.8) and click `OK`. IntelliJ will
automatically detect the `build.sbt` file and configure the Scala/SBT project. You will
need to set `automation` as the root source and accept the suggestion to add the git
root. In your firecloud-ui clojure project you may want to choose the automation 
directory and set it to ignore. If you add a new file in the scala project and choose 
not to add it to the git repo, you may be asked again in the clojure project.
