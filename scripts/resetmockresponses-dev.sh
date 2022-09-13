#!/bin/bash
IFS=$'\n\t'
set -euo pipefail

curl -X POST http://localhost:8080/__admin/mappings/reset
curl --data-binary @config/wiremock-proxy-dev.json http://localhost:8080/__admin/mappings/new
