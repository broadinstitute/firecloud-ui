#!/bin/bash
set -euox pipefail
IFS=$'\n\t'

VAULT_TOKEN=${VAULT_TOKEN:-$(<~/.vault-token)}

if [[ "$PWD" != "$HOME"* ]]; then
  echo 'Docker does not support mounting directories outside of your home directory.'
  exit 1;
fi

docker run --rm -it -v "$PWD":/working broadinstitute/dsde-toolbox \
  render-templates.sh local $VAULT_TOKEN

cp src/main/config/dsde-local/config.json target
