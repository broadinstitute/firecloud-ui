#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

./script/common/clean-build-dir.sh
./script/common/create-index-html.sh dev
./script/common/symlink-assets.sh

lein cljsbuild once
