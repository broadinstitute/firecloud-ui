#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# Auto-build watches files in the src directory and rebuilds the JavaScript files whenever they
# change. For releases, this can be helpful when tracking down release-only bugs.

./script/common/build.sh auto release

