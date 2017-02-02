#!/bin/bash

docker run --rm \
  -w /work \
  -v "$PWD/$PROJECT":/work \
  -v maven-cache:/root/.m2 \
  broadinstitute/clojure-node \
  lein with-profile deploy do npm install, clean, resource, cljsbuild once
