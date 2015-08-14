#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# Creates a release build.

./script/common/build.sh once release

