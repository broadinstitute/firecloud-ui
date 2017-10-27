# Testing

Unit tests are written in ClojureScript, and UI tests are written using Selenium in Scala.

## ClojureScript Tests

To run all tests, enter this at the JavaScript console with the app running:

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

## Selenium tests

Selenium tests are found in the `automation` directory. They must be run against a properly-populated instance of FireCloud. Currently, we support firecloud-in-a-box (FiaB) as started and populated by the `fiab-start` job in Jenkins. See [Firecloud-in-a-Box](https://broadinstitute.atlassian.net/wiki/spaces/GAWB/pages/114755655/Firecloud-in-a-Box) for instructions.

### IntelliJ Setup

There are effectively two projects in this repository: a ClojureScript project for FireCloud UI and a Scala project for the Selenium-based UI tests. They are logically linked because the tests rely on the rendered DOM structure (primarily `data-test-id` attributes) but are otherwise largely separate. Therefore (and also in part because of [this issue](https://youtrack.jetbrains.com/issue/SCL-12358)) we recommend opening separate IntelliJ projects for each.

For the Scala project, choose `File -> New -> Project from Existing Sources...` and select the `automation` directory inside the git clone, then `Import project from external model` and select `SBT`. IntelliJ will automatically detect the `build.sbt` file and configure the Scala/SBT project. You will need to accept the suggestion to add the git root. In your firecloud-ui clojure project you may want to choose the automation directory and set it to ignore. Otherwise, if you add a new file in the scala project and choose not to add it to the git repo, you may be asked again when you switch over to the clojure project.


### Running with docker ("headless")

Before you run tests for the first time, make sure your docker has enough memory allocated. Go to `Docker -> Preferences -> Advanced` and set the memory to at least 4 GB. 

To generate a Docker container automatically and run the tests inside of it:

```bash
./run-tests.sh FIRECLOUD-LOCATION [dev | qa] [vault token] [working dir]
```

**Arguments:** (arguments are positional)

* FireCloud location (required)
	* One of `fiab`, `local` (local UI, FIAB backend), `alpha`, `prod`, or an IP address. `fiab` and `local` will pull the IP from your `etc/hosts`.
* `dev` or `qa`
	* Environment of your FiaB. Defaults to `dev`.
* Vault auth token
	* Defaults to reading it from `~/.vault-token`.
* Working directory
	* Defaults to `$PWD`.

### Running directly (real Chrome)

If you have a FiaB running and its IP is configured in your `/etc/hosts`, you can run tests locally and watch them execute in a browser. **IMPORTANT**: Be careful about interacting with your computer while running tests this way because stray keyboard and mouse interactions can interfere with test execution. It is recommended to use this only when writing/debugging specific tests, not running the entire test suite. 

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

**Arguments:** (arguments are positional)

* Working directory
	* Defaults to `$PWD`.
* Vault auth token
	* Defaults to reading it from `~/.vault-token`.
* `dev` or `qa`
	* Environment of your FiaB. Defaults to `dev`.
* Local UI
	* Enter `local_ui` here to run against a local UI stack.

#### Using a local UI

Be sure you used the `local_ui` param when you rendered your configs (see above). When starting your UI, run:

```bash
FIAB=true [ENV=qa] ./config/docker-rsync-local-ui.sh
```

If you don't provide `ENV`, it will default to `dev`.

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
