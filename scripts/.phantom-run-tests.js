var page = require('webpage').create();

var logToken = 'e54b0a2b-0934-400f-bf3a-68d01dcde2df';
var loggingWorks = false;
var someTestsFailed = false;

function installConsoleListener() {
  page.onConsoleMessage = function(msg) {
    if (msg.substring(0, logToken.length) === logToken) {
      var commandString = msg.substring(logToken.length);
      if (commandString === 'started') {
        loggingWorks = true;
      } else if(commandString === 'fail') {
        someTestsFailed = true;
      }
    } else {
      console.log(msg);
    }
  };
}

page.open('http://server/', function(status) {
  installConsoleListener();
  page.evaluate(function(logToken) {
    console.log(logToken + 'started');
    console.group = function(group) { console.log(group + " {") };
    console.groupEnd = function() { console.log("}") };
    broadfcuitest.utils.report_test_status = function(isSuccess, failureCount, errorCount) {
      if(isSuccess) {
        console.log('All tests passed.');
      } else {
        console.log('Tests failed: ' + failureCount + ' failures and ' + errorCount + ' errors');
        console.log(logToken + 'fail');
      }
    };
    broadfcuitest.testrunner.run_all_tests();
  }, logToken);
  if (!loggingWorks) {
    console.log('Failed to read test output.');
    phantom.exit(2);
  } else if (someTestsFailed) {
    phantom.exit(1);
  } else {
    phantom.exit();
  }
});
