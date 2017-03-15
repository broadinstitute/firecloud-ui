var page = require('webpage').create();

// Must match variable of the same name below.
const logToken = 'e54b0a2b-0934-400f-bf3a-68d01dcde2df';
var someTestsFailed = false;

function installConsoleListener() {
  page.onConsoleMessage = function(msg) {
    if (msg.substring(0, logToken.length) == logToken) {
      var commandString = msg.substring(logToken.length);
      if(commandString == 'fail') {
        someTestsFailed = true;
      }
    } else {
      console.log(msg);
    }
  };
}

page.open('http://server/', function(status) {
  installConsoleListener();
  page.evaluate(function() {
    const logToken = 'e54b0a2b-0934-400f-bf3a-68d01dcde2df';
    console.group = function(group) { console.log(group + " {"); };
    console.groupEnd = function() { console.log("}"); };
    broadfcuitest.utils.report_test_status = function(isSuccess, failureCount, errorCount) {
      if(isSuccess) {
        console.log('All tests passed.');
      } else {
        console.log('Tests failed: ' + failureCount + ' failures and ' + errorCount + ' errors');
        console.log(logToken + 'fail');
      }
    }
    broadfcuitest.testrunner.run_all_tests();
  });
  if (someTestsFailed) {
    phantom.exit(1);
  } else {
    phantom.exit();
  }
});
