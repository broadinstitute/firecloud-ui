#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

docker rm -f orch || true
docker rm -f ui || true

docker run -d --name orch \
  -e GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID" \
  -e AGORA_URL_ROOT="$AGORA_URL_ROOT" -e RAWLS_URL_ROOT="$RAWLS_URL_ROOT" \
  -e SERVER_NAME="$SERVER_NAME" \
  -v /etc/localtime:/etc/localtime:ro \
  broadinstitute/firecloud-orchestration

docker run -d --name ui \
  -e GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID" \
  -e ORCH_URL_ROOT='http://orch:8080' \
  -p 80:80 -p 443:443 --link orch \
  -v /etc/localtime:/etc/localtime:ro \
  -v /etc/server.crt:/etc/ssl/certs/server.crt:ro \
  -v /etc/server.key:/etc/ssl/private/server.key:ro \
  -v /etc/ca-bundle.crt:/etc/ssl/certs/ca-bundle.crt:ro \
  broadinstitute/firecloud-ui
