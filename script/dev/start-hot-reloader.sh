#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# Starts figwheel so changes are immediately loaded into the running browser window and appear
# without requiring a page reload.

./script/common/clean-build-dir.sh
./script/common/create-index-html.sh dev
./script/common/symlink-assets.sh

lein figwheel
