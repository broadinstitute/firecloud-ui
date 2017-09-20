# FireCloud UI

FireCloud user interface for web browsers.

https://portal.firecloud.org

## Technologies

[ClojureScript](https://github.com/clojure/clojurescript) is used for the UI.

We use the [Leiningen](http://leiningen.org/) build tool to manage ClojureScript dependencies.

The code incorporates usage of [react-cljs](https://github.com/dmohs/react-cljs) which is a ClojureScript wrapper for [React](https://facebook.github.io/react/).

Figwheel replaces the running JavaScript within the page so changes are visible without a browser reload. More information [here](https://github.com/bhauman/lein-figwheel). [This video](https://www.youtube.com/watch?v=j-kj2qwJa_E) gives some insight into the productivity gains available when using this technology (up to about 15:00 is all that is necessary).

[Scala](http://www.scala-lang.org/) and [Selenium](http://seleniumhq.org/) are used for full-system UI integration tests.

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

## Testing ClojureScript

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

## IntelliJ

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
need to accept the suggestion to add the git root. In your firecloud-ui clojure project
you may want to choose the automation directory and set it to ignore. If you add a new
file in the scala project and choose not to add it to the git repo, you may be asked
again when you switch over to the clojure project.

## Selenium tests

Selenium tests are found in the `automation` directory. They must be run against a
properly-populated instance of FireCloud. Currently, we support firecloud-in-a-box (FiaB) as started
and populated by the `fiab-start` job in Jenkins. See
[Firecloud-in-a-Box](https://broadinstitute.atlassian.net/wiki/spaces/GAWB/pages/114755655/Firecloud-in-a-Box)
for instructions.

### Running with docker ("headless")

Before you run tests for the first time, make sure your docker has enough memory allocated. Go to
Docker->Preferences->Advanced and set the memory to at least 4 GB. 

First build the docker image containing the test code. _IMPORTANT_: You will need to rebuild this
docker image any time you make a change to the test code. From the `automation` directory:

```bash
docker build -f Dockerfile-tests -t automation .
```

Then run the tests from the newly created image. This script will render the
`application.conf` and `firecloud-account.pem` to be used by the test container using secrets read
from vault. Note that if you are running outside of docker you will need to generate these files
manually (see below). From the `automation/docker` directory:

```bash
./run-tests.sh <# of nodes> <dev or qa> <ip of FiaB> automation $(cat ~/.vault-token)
```

Using 3 nodes should provide sufficient parallelization while fitting inside the recommended 4 GB
docker memory setting when running on a developer workstation.

### Running directly (real Chrome)

If you have a FiaB running and its IP is configured in your `/etc/hosts`, you can run tests locally
and watch them execute in a browser. _IMPORTANT_: Be careful about interacting with your computer
while running tests this way because stray keyboard and mouse interactions can interfere with test
execution. It is recommended to use this only when writing/debugging specific tests, not running the
entire test suite. 

#### Set Up

Before you run the tests for the first time, make sure you have chromedriver installed. On Mac OS,
Homebrew (`brew`) is recommended:

```bash
brew install chromedriver
```

If your version of Chrome is 61 or later, check `chromedriver --version` to make sure you're up to
at least 2.32. If not, use `brew upgrade chromedriver` to update.
 
Also run the config render script. This will render the necessary `application.conf` and
`firecloud-account.pem` for the tests. From the `automation` directory:

```bash
./render-local-env.sh $PWD $(cat ~/.vault-token) <dev or qa>
```

#### Running tests

##### From IntelliJ

First, you need to set some default VM parameters for ScalaTest run configurations. In IntelliJ, go
to `Run` > `Edit Configurations...`, select `ScalaTest` under `Defaults`, and add these VM
parameters:
```
-Djsse.enableSNIExtension=false -Dheadless=false
```

Also make sure that there is a `Build` task configured to run before launch.

Now, simply open the test spec, right-click on the class name or a specific test string, and select
`Run` or `Debug` as needed. A good one to start with is `GoogleSpec` to make sure your base
configuration is correct.

##### From the command line

To run all tests:

```bash
sbt test -Djsse.enableSNIExtension=false -Dheadless=false
```

To run a single suite:

```bash
sbt -Djsse.enableSNIExtension=false -Dheadless=false "testOnly *GoogleSpec"
```

To run a single test within a suite:

```bash
# matches test via substring
sbt -Djsse.enableSNIExtension=false -Dheadless=false "testOnly *GoogleSpec -- -z \"have a search field\""
```

For more information see: http://www.scala-sbt.org/0.13/docs/Testing.html#Test+Framework+Arguments

##### Against a local UI

To run tests against firecloud-ui running on your local workstation, you will need to edit the
`baseURL` config parameter in the `automation/src/test/resources` produced by `render-local-env.sh`:

```
baseUrl = "http://local.broadinstitute.org/"
```

When starting your UI, run:

```bash
FIAB=true ENV=<dev or qa> ./config/docker-rsync-local-ui.sh
```

## Troubleshooting

If you have problems with IntelliJ, it may be due to artifacts left over from a previous import or
build. If you have problems, first close the projects in IntelliJ and delete the `automation/.idea`
and `automation/target` directories. Then repeat the IntelliJ project import instructions above.

If FireCloud tests fail, make sure your basic selenium test setup is working by first running
GoogleSpec which only accesses http://www.google.com/ and does not rely on FireCloud.