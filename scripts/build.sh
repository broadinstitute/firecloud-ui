#!/bin/bash
IFS=$'\n\t'
set -euxo pipefail

docker run --rm \
  -w /work \
  -v "$PWD":/work \
  -v jar-cache:/root/.m2 \
  -v node-modules:/work/node_modules \
  -e npm_config_unsafe_perm="true" \
  broadinstitute/clojure-node \
  bash -c 'lein with-profile deploy do clean, resource, cljsbuild once && npm install && npm run webpack -- -p'
