#!/bin/bash
IFS=$'\n\t'
set -euxo pipefail

docker run --rm \
  -w /work \
  -v "$PWD":/work \
  -v maven-cache:/root/.m2 \
  broadinstitute/clojure-node \
  lein with-profile deploy do npm install, clean, resource, cljsbuild once
