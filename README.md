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

*To be continued...*
