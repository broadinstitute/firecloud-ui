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

##### From intellij

Edit your run config defaults for ScalaTest.
Add this to the VM parameters: `-Djsse.enableSNIExtension=false  -Dheadless=false`

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
We now recommend opening a separate IntelliJ project for the automation test code. From 
IntelliJ choose File->New->Project From Existing Sources and select automation/build.sbt.
You will need to set automation as the root source and accept the suggestion to add the 
git root. In your firecloud-ui clojure project you may want to choose the automation 
directory and set it to ignore. If you add a new file in the scala project and choose 
not to add it to the git repo, you may be asked again in the clojure project.

For reference, these are the old instructions for the combined project:
After opening the project, if IntelliJ shows errors in scala source or running a test
throws a `MethodNotFoundException`, open the SBT panel and click the button for "Refresh
all SBT projects" (and keep reading).

If IntelliJ shows errors in cljs source (which will happen after refreshing SBT projects),
open the Leiningen panel and click the button for "Refresh Leiningen Projects". Then open
Project Structure > Modules > firecloue-ui > Paths and change "Test output path" to be
different than the value for "Output path", e.g.
`/{your firecloud-ui project root}/firecloud-ui/resources/public/target/test-classes`
(and keep reading to learn why).

There is currently an issue with IntelliJ's scala/SBT support that causes the scala build
to incorrectly pay attention to unrelated (i.e. Leiningen) modules. Cursive sets both
"Output path" and "Test output path" to the same value. The result is that, when building
the scala code, you'll see an error about shared compile output paths. An issue has been
filed against IntelliJ: [https://youtrack.jetbrains.com/issue/SCL-12358](). (Please vote for
it if you want to see it fixed.) A workaround feature for Cursive has been requested in a
related issue: [https://github.com/cursive-ide/cursive/issues/282]().
