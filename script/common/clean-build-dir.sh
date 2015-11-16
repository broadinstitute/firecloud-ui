#!/bin/bash
set -euox pipefail
IFS=$'\n\t'

mkdir -p target
# Instead of removing the directory, we remove all files in it. This ensures that if there is
# already something using the directory (e.g., a web server serving files from it) it doesn't get
# screwed up.
# Also, don't clobber the config directory.
find target -mindepth 1 -maxdepth 1 -and -not -name 'config' -exec rm -rf '{}' ';'
