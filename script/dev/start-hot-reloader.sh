#!/bin/bash
set -euo pipefail
IFS=$'\n\t'
set -x

# Starts figwheel so changes are immediately loaded into the running browser window and appear
# without requiring a page reload.

exec docker exec -it firecloud_ui_1 \
  sh -c "BUILD_TYPE='dev'"' rlfe ./script/common/build.sh hot-reload'
