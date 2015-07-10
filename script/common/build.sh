#!/bin/bash -x
set -euo pipefail
IFS=$'\n\t'

BUILD_TYPE=${1:-}
BUILD_CONFIG=${2:-}

if [[ -z "$BUILD_TYPE" ]]; then
  echo "Usage: $0 ( once | auto | hot-reload ) [ release ]"
  exit 1
fi

if [ "$BUILD_CONFIG" == 'release' -a "$BUILD_TYPE" == 'hot-reload' ]; then
  echo 'Hot reloading is not supported on release builds.'
  exit 1
fi

./script/common/clean-build-dir.sh
./script/common/create-index-html.sh "$BUILD_CONFIG"
./script/common/symlink-assets.sh

function run-lein() {
  if [ "$BUILD_CONFIG" == 'release' ]; then
    lein with-profile release $@
  else
    lein $@
  fi
}

if [ "$BUILD_TYPE" == 'hot-reload' ]; then
  run-lein figwheel
else
  run-lein cljsbuild $BUILD_TYPE
fi
