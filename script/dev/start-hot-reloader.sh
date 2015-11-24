#!/bin/bash
set -euox pipefail
IFS=$'\n\t'

# Starts figwheel so changes are immediately loaded into the running browser window and appear
# without requiring a page reload.

exec docker exec -it firecloud_ui_1 \
  sh -c "BUILD_TYPE='dev'"' rlfe ./script/common/build.sh hot-reload'
