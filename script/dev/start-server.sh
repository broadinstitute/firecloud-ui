#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# Starts server for development.
# - exposes Figwheel port
# - allows HTTP since Figwheel does not support HTTPS
# - mounts PWD over deployed code

ORCH_URL_ROOT=${ORCH_URL_ROOT:-'https://firecloud.dsde-dev.broadinstitute.org/api'}

exec docker run --rm --name firecloud-ui -p 80:80 -p 443:443 \
  -p 3449:3449 \
  -e GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID" \
  -e ORCH_URL_ROOT="$ORCH_URL_ROOT" \
  -e HTTPS_ONLY=false \
  -v "$PWD":/app \
  firecloud-ui
