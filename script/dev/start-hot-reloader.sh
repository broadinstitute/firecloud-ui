#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# Starts figwheel so changes are immediately loaded into the running browser window and appear
# without requiring a page reload.

exec docker exec -it firecloud-ui rlfe ./script/common/build.sh hot-reload
