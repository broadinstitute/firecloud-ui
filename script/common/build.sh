#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

BUILD_TYPE=${BUILD_TYPE:-'dev'}
BUILD_PATTERN=${1:-}

if [[ -z "$BUILD_PATTERN" ]]; then
  echo "Usage: $0 ( once | auto | hot-reload )"
  exit 1
fi

if [ "$BUILD_TYPE" == 'minimized' -a "$BUILD_PATTERN" == 'hot-reload' ]; then
  echo 'Hot reloading is not supported on minimized builds.'
  exit 1
fi

./script/common/clean-build-dir.sh
./script/common/create-index-html.sh
./script/common/symlink-assets.sh

function run-lein() {
  if [ "$BUILD_TYPE" == 'minimized' ]; then
    lein with-profile minimized $@
  else
    lein $@
  fi
}

if [ "$BUILD_PATTERN" == 'hot-reload' ]; then
  run-lein figwheel
else
  run-lein cljsbuild $BUILD_PATTERN
fi
