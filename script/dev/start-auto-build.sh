#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# Auto-build watches files in the src directory and rebuilds the JavaScript files whenever they
# change.

./script/common/clean-build-dir.sh
./script/common/create-index-html.sh dev
./script/common/symlink-assets.sh

lein cljsbuild auto
