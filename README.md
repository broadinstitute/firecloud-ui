# FireCloud UI

FireCloud user interface for web browsers.

https://portal.firecloud.org

## Technologies

[ClojureScript](https://github.com/clojure/clojurescript) is used for the UI.

We use the [Leiningen](http://leiningen.org/) build tool to manage ClojureScript dependencies, and [npm](https://www.npmjs.com) for Javascript dependencies.

The code incorporates usage of [react-cljs](https://github.com/dmohs/react-cljs) which is a ClojureScript wrapper for [React](https://facebook.github.io/react/).

Figwheel replaces the running JavaScript within the page so changes are visible without a browser reload. More information [here](https://github.com/bhauman/lein-figwheel). [This video](https://www.youtube.com/watch?v=j-kj2qwJa_E) gives some insight into the productivity gains available when using this technology (up to about 15:00 is all that is necessary).

[Scala](http://www.scala-lang.org/) and [Selenium](http://seleniumhq.org/) are used for full-system UI integration tests.

## Developing

FireCloud is currently supported within Broad's engineering environment:

https://github.com/broadinstitute/firecloud-develop

Support for running FireCloud outside of the Broad is planned.

### Styles and Conventions

**Before you get started**, take a look at [CONTRIBUTING.md](contributing.md)

### Setup

1. Make sure you've run the `firecloud-setup.sh` script in the `firecloud-develop` repo to render the config files and run script.
2. Be sure you've added `127.0.0.1       local.broadinstitute.org` to your `/etc/hosts`.

### Starting the development server

```bash
./config/docker-rsync-local-ui.sh
```

You must be viewing the application via HTTP. HTTPS is not supported by Figwheel.

This can take around 40 seconds to completely start. When ready, it will display the following message:

```
Prompt will show when figwheel connects to your application
```

To connect, load [`http://local.broadinstitute.org`](http://local.broadinstitute.org) in your browser (Chrome is strongly recommended for development). The prompt should appear less than ten seconds after you reload the page. If it is not connecting, make sure to check the JavaScript console for error messages.

### Testing ClojureScript

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

### Building

To compile the clojure project into the target directory, run 

```bash
./script/build.sh compile
```

To compile and build the `broadinstitute/firecloud-ui` docker image, run

```bash
./script/build.sh compile -d build
```

## IntelliJ

There are effectively two projects in this repository: a ClojureScript project for FireCloud UI and a Scala project for the Selenium-based UI tests. They are logically linked because the tests rely on the rendered DOM structure (primarily `data-test-id` attributes) but are otherwise largely separate. Therefore (and also in part because of [this issue](https://youtrack.jetbrains.com/issue/SCL-12358)) we recommend opening separate IntelliJ projects for each.

For the ClojureScript project, choose `File -> New -> Project from Existing Sources...` and select the base `firecloud-ui`directory of your git clone. If prompted for a Project JDK, select a recent Java SDK to use. Assuming you have the Cursive plugin installed, IntelliJ will automatically detect the `project.clj` file and configure the CLJS Leiningen project.

For the Scala project, choose `File -> New -> Project from Existing Sources...` and select the `automation` directory inside the git clone. Select a recent Java SDK (i.e. 1.8) and click `OK`. IntelliJ will automatically detect the `build.sbt` file and configure the Scala/SBT project. You will need to accept the suggestion to add the git root. In your firecloud-ui clojure project you may want to choose the automation directory and set it to ignore. Otherwise, if you add a new file in the scala project and choose not to add it to the git repo, you may be asked again when you switch over to the clojure project.

## Selenium tests

Selenium tests are found in the `automation` directory. They must be run against a properly-populated instance of FireCloud. Currently, we support firecloud-in-a-box (FiaB) as started and populated by the `fiab-start` job in Jenkins. See [Firecloud-in-a-Box](https://broadinstitute.atlassian.net/wiki/spaces/GAWB/pages/114755655/Firecloud-in-a-Box) for instructions.

### Running with docker ("headless")

Before you run tests for the first time, make sure your docker has enough memory allocated. Go to `Docker -> Preferences -> Advanced` and set the memory to at least 4 GB. 

To generate a Docker container automatically and run the tests inside of it:

```bash
./run-tests.sh FIRECLOUD-LOCATION [dev | qa] [vault token] [working dir]
```

**Arguments:**

* FireCloud location (required)
	* One of `fiab`, `local`, `alpha`, `prod`, or an IP address. `fiab` will pull the IP from your `etc/hosts`.
* `dev` or `qa`
	* Environment of your FiaB. Defaults to `dev`.
* Vault auth token
	* Defaults to reading it from `~/.vault-token`.
* Working directory
	* Defaults to `$PWD`.

### Running directly (real Chrome)

If you have a FiaB running and its IP is configured in your `/etc/hosts`, you can run tests locally and watch them execute in a browser. _IMPORTANT_: Be careful about interacting with your computer while running tests this way because stray keyboard and mouse interactions can interfere with test execution. It is recommended to use this only when writing/debugging specific tests, not running the entire test suite. 

#### Set Up

Before you run the tests for the first time, make sure you have chromedriver installed. On Mac OS, [Homebrew](https://brew.sh) is recommended:

```bash
brew install chromedriver
```

If your version of Chrome is 61 or later, check `chromedriver --version` to make sure you're up to at least 2.32. If not, use `brew upgrade chromedriver` to update.
 
Also run the config render script. If you are planning on running the firecloud ui locally, add the local_ui param (it will set the baseUrl to "http://local.broadinstitute.org/". This will render the necessary `application.conf` and `firecloud-account.pem` for the tests. From the `automation` directory:

```bash
./render-local-env.sh [working dir] [vault token] [dev | qa] [local_ui]
```

**Arguments:**

* Working directory
	* Defaults to `$PWD`.
* Vault auth token
	* Defaults to reading it from `~/.vault-token`.
* `dev` or `qa`
	* Environment of your FiaB. Defaults to `dev`.
* Local UI
	* Enter `local_ui` here to run against a local UI stack.


#### Running tests

##### From IntelliJ

First, you need to set some default VM parameters for ScalaTest run configurations. In IntelliJ, go to `Run` > `Edit Configurations...`, select `ScalaTest` under `Defaults`, and add these VM parameters:
```
-Djsse.enableSNIExtension=false -Dheadless=false
```

Also make sure that there is a `Build` task configured to run before launch.

Now, simply open the test spec, right-click on the class name or a specific test string, and select `Run` or `Debug` as needed. A good one to start with is `GoogleSpec` to make sure your base configuration is correct. All test code lives in `automation/src/test/scala`. FireCloud test suites can be found in `automation/src/test/scala/org/broadinstitute/dsde/firecloud/test`.

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

For more information see [SBT's documentation](http://www.scala-sbt.org/0.13/docs/Testing.html#Test+Framework+Arguments).

##### Against a local UI

Be sure you used the `local_ui` param when you rendered your configs (see above). When starting your UI, run:

```bash
FIAB=true [ENV=qa] ./config/docker-rsync-local-ui.sh
```

If you don't provide `ENV`, it will default to `dev`.

## Troubleshooting

If you have problems with IntelliJ, it may be due to artifacts left over from a previous import or build. If you have problems, first close the projects in IntelliJ and delete the `automation/.idea` and `automation/target` directories. Then repeat the IntelliJ project import instructions above.

If FireCloud tests fail, make sure your basic selenium test setup is working by first running GoogleSpec which only accesses http://www.google.com/ and does not rely on FireCloud.
