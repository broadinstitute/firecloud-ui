#!/bin/bash
set -euo pipefail
IFS=$'\n\t'
set -x

# Auto-build watches files in the src directory and rebuilds the JavaScript files whenever they
# change.

exec docker exec -it firecloud_ui_1 sh -c "BUILD_TYPE='dev'"' ./script/common/build.sh auto'
