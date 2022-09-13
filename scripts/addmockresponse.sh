#!/bin/bash
IFS=$'\n\t'
set -euo pipefail


MOCK_JSON="${1:-}"
if [[ -z "$MOCK_JSON" ]]; then
  echo
  echo 'Expected Wiremock JSON file'
  exit 1
fi

if [[ ! -e "$MOCK_JSON" ]]; then
  echo
  echo 'File does not exist: '"$MOCK_JSON"
  exit 1
fi

curl --data-binary @"$MOCK_JSON" http://localhost:8080/__admin/mappings/new
