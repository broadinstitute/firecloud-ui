#!/bin/bash
set -euox pipefail
IFS=$'\n\t'

./script/clean-build-dir.sh
exec docker run --rm -it -w /work -v "$PWD":/work -v "$HOME"/.m2:/root/.m2 \
  clojure \
  rlfe lein with-profile deploy do resource, cljsbuild once
