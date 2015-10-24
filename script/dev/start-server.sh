#!/bin/bash
set -euox pipefail
IFS=$'\n\t'

# Starts server for development.
# - exposes Figwheel port (3449)
# - allows HTTP since Figwheel does not support HTTPS
# - mounts PWD over deployed code
# - overrides the build type to use non-minimized code

if [[ "$PWD" != "$HOME"* ]]; then
  echo 'Docker does not support mounting directories outside of your home directory.'
  exit 1;
fi

docker run --rm -it -v "$PWD":/working broadinstitute/dsde-toolbox render-templates.sh local $VAULT_TOKEN

docker-compose -p firecloud -f target/config/docker-compose.yaml up -d
