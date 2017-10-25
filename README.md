# FireCloud UI

FireCloud user interface for web browsers.

https://portal.firecloud.org

## Technologies

[ClojureScript](https://github.com/clojure/clojurescript) is used for the UI.

We use the [Leiningen](http://leiningen.org/) build tool to manage ClojureScript dependencies, and [npm](https://www.npmjs.com) for Javascript dependencies.

The code incorporates usage of [react-cljs](https://github.com/dmohs/react-cljs) which is a ClojureScript wrapper for [React](https://facebook.github.io/react/).

Figwheel replaces the running JavaScript within the page so changes are visible without a browser reload. More information [here](https://github.com/bhauman/lein-figwheel). [This video](https://www.youtube.com/watch?v=j-kj2qwJa_E) gives some insight into the productivity gains available when using this technology (up to about 15:00 is all that is necessary).

[Scala](http://www.scala-lang.org/) and [Selenium](http://seleniumhq.org/) are used for full-system UI integration tests.

## IntelliJ Setup
Choose `File -> New -> Project from Existing Sources...` and select the base `firecloud-ui`directory of your git clone. With the Cursive plugin installed, you can select `Import project from external model` and then select `Leiningen`. If prompted for a Project SDK, select a recent Java SDK to use.

## Developing

FireCloud is currently supported within Broad's engineering environment:

https://github.com/broadinstitute/firecloud-develop

Support for running FireCloud outside of the Broad is planned.

### Styles and Conventions

**Before you get started**, take a look at [CONTRIBUTING.md](contributing.md)

### Development Stack Setup

1. Make sure you've run the `firecloud-setup.sh` script in the `firecloud-develop` repo to render the config files and run script.
2. Be sure you've added local.broadinstitute.org to your `/etc/hosts`:

``` bash
sudo sh -c "echo '127.0.0.1       local.broadinstitute.org' >> /etc/hosts"
```

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

### Building

To compile the clojure project into the target directory, run 

```bash
./script/build.sh compile
```

To compile and build the `broadinstitute/firecloud-ui` docker image, run

```bash
./script/build.sh compile -d build
```

## Testing
See [TESTING.md](TESTING.md)

## Troubleshooting

If you have problems with IntelliJ, it may be due to artifacts left over from a previous import or build. If you have problems, first close the projects in IntelliJ and delete the `automation/.idea` and `automation/target` directories. Then repeat the IntelliJ project import instructions above.

If FireCloud tests fail, make sure your basic selenium test setup is working by first running GoogleSpec which only accesses http://www.google.com/ and does not rely on FireCloud.
