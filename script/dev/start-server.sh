#!/bin/bash
set -euo pipefail
IFS=$'\n\t'
set -x

# Starts server for development.
# - exposes Figwheel port (3449)
# - allows HTTP since Figwheel does not support HTTPS
# - mounts PWD over deployed code
# - overrides the build type to use non-minimized code

if [[ "$PWD" != "$HOME"* ]]; then
  echo 'Docker does not support mounting directories outside of your home directory.'
  exit 1;
fi

ORCH_URL_ROOT=${ORCH_URL_ROOT:-'https://firecloud.dsde-dev.broadinstitute.org/service'}

exec docker run --rm --name firecloud-ui -p 80:80 -p 443:443 \
  -p 3449:3449 \
  -e GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID" \
  -e ORCH_URL_ROOT="$ORCH_URL_ROOT" \
  -e HTTPS_ONLY=false \
  -e BUILD_TYPE='' \
  -v "$PWD":/app \
  "$@" \
  broadinstitute/firecloud-ui
