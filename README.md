# firecloud-ui 

FireCloud user interface for web browsers.

## Getting Started

We use the Leiningen build tool. To install, follow the installation instructions on the [Leiningen web site](http://leiningen.org/).

## Building

Run the Leiningen task to build the project's ClojureScript files into JavaScript:
```
lein cljsbuild once
```

## Running

Open `src/static/index.html` in any browser.

Alternatively, run `python -m SimpleHTTPServer` from the project root and point your browser to
[http://127.0.0.1:8000/src/static/](http://127.0.0.1:8000/src/static/) 

## Testing

Run the Leiningen task to run tests:
```
lein test
```

## Development Workflow

To speed development, Leiningen will rebuild the project whenever the ClojureScript files are changed:
```
lein cljsbuild auto
```

In Intellij, you can set up a new run task to call the above command with a single button click in the IDE:

* Name: `cljsbuild`
* Script: `/usr/local/Cellar/leiningen/2.5.1/bin/lein` or wherever lein is installed on your system
* Program Arguments: `cljsbuild once`
* Working Directory: `your project directory`


*To be continued...*
