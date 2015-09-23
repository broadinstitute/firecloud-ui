#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# Auto-build watches files in the src directory and rebuilds the JavaScript files whenever they
# change.

exec docker exec -it firecloud-ui ./script/common/build.sh auto
