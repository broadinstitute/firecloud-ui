## Guidelines for automated UI tests

* Tests should only verify one behavior. Tests should be simple and as short as possible, for the purpose of maintainability, readability, and speed.
* Every test should be able to run independently (no dependency to other tests) and in parallel (does not interfere with other tests).
* Time.sleep() is deeply frowned upon.
* Elements are selected by unique ID-locators (the attribute “data-test-id”).
* The test should use the Page Object Model, including:
    * Clean separation between test code (which is high level) and page specific code (such as locators / element).
    * Use proper scoping for functions and variables.
    * Declare element ID-locators only once, and as a constant.
* The test should cleanup after itself.
* Keep in mind that this test will be required to pass for every subsequent build. If the test is flaky, investigate and fix the flakiness before merging.
* New tests should be kept under 5 minutes.

For more documentation on writing and running tests, see the [Confluence page](https://broadinstitute.atlassian.net/wiki/spaces/GAWB/pages/116428999/Creating+and+running+Automated+Tests). 

To run against alpha:
```$xslt
./run-tests-against-real.sh alpha $VAULT_TOKEN
```
To run smoketests against prod:
```$xslt
./run-tests-against-real.sh prod $VAULT_TOKEN
```