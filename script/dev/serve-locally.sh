#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

pushd target
ruby -run -ehttpd . -p8000
popd
