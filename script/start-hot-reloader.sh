#!/bin/bash
set -euox pipefail
IFS=$'\n\t'

# Starts figwheel so changes are immediately loaded into the running browser window and appear
# without requiring a page reload.

./script/clean-build-dir.sh
exec docker run --rm -it \
  -w /work -v "$PWD":/work -v "$HOME"/.m2:/root/.m2 \
  -e FIGWHEEL_HOST="${FIGWHEEL_HOST:-}" \
  -p 3449:3449 \
  dmohs/clojure \
  rlfe lein do resource, figwheel
