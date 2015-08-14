<html>
  <head profile="http://www.w3.org/2005/10/profile">
    <meta charset="utf-8">
    <meta http-equiv="Content-type" content="text/html;charset=utf-8">
    <meta name="viewport" content="initial-scale=1,width=device-width">
    <meta name="google-signin-scope" content="profile email">
    <meta name="google-signin-client_id" content="806222273987-2ntvt4hnfsikqmhhc18l64vheh4cj34q.apps.googleusercontent.com">
    <title>FireCloud | Broad Institute</title>
    <link href='//fonts.googleapis.com/css?family=Roboto:400,500,700' rel='stylesheet' type='text/css'>
    <style>
      body {
        margin: 0;
        font-family: 'Roboto', sans-serif;
        -webkit-font-smoothing: antialiased;
        -moz-font-smoothing: antialiased;
        -o-font-smoothing: antialiased;
      }
      @font-face {
        font-family: "fontIcons";
        src: url("assets/icons/fontIcons.eot");
        src: url("assets/icons/fontIcons.eot?#iefix") format("eot"),
          url("assets/icons/fontIcons.woff") format("woff"),
          url("assets/icons/fontIcons.ttf") format("truetype"),
          url("assets/icons/fontIcons.svg#fontIcons") format("svg");
      }
    </style>
    <link rel="icon" type="image/x-icon" href="assets/favicon.ico">
    <script src="https://apis.google.com/js/platform.js" async defer></script>
    <script>
      (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
      (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
      m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
      })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

      ga('create', 'UA-64736463-1', 'auto');
      ga('send', 'pageview');
    </script>
    <?php if ($argv[1] != 'release') { ?>
      <script src="build/goog/base.js"></script>
    <?php } ?>
  </head>
  <body>
    <div id="contentRoot"></div>
    <script src="compiled.js"></script>
    <?php if ($argv[1] != 'release') { ?>
      <script>goog.require('org.broadinstitute.firecloud_ui.main');</script>
    <?php } ?>
    <script>
      var app = org.broadinstitute.firecloud_ui.main.render(document.getElementById('contentRoot'));
      function onSignIn(googleUser) {
        app.handleSignIn(googleUser);
      }
    </script>
  </body>
</html>
