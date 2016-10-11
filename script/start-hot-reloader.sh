#!/bin/bash
set -euox pipefail
IFS=$'\n\t'

# Starts figwheel so changes are immediately loaded into the running browser window and appear
# without requiring a page reload.

./script/clean-build-dir.sh
ln -s target resources
exec docker run --rm -it \
  -w /work -v "$PWD":/work -v "$HOME"/.m2:/root/.m2 \
  -e FIGWHEEL_HOST="${FIGWHEEL_HOST:-}" \
  -p 80:80 \
  clojure \
  rlfe lein do resource, figwheel
