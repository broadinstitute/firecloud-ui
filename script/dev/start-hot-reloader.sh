#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# Starts figwheel so changes are immediately loaded into the running browser window and appear
# without requiring a page reload.

if which rlfe; then
  WRAPPER='rlfe '
elif which rlwrap; then
  WRAPPER='rlwrap '
else
  WRAPPER=''
fi

exec bash -c "$WRAPPER"'./script/common/build.sh hot-reload'
