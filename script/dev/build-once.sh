#!/bin/bash
set -euo pipefail
IFS=$'\n\t'
set -x

exec docker exec -it firecloud_ui_1 sh -c "BUILD_TYPE='dev'"' ./script/common/build.sh once'
