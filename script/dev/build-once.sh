#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

exec docker exec -it firecloud-ui ./script/common/build.sh once
