#!/bin/bash
set -euox pipefail
IFS=$'\n\t'

if [ ! -e target/config/firecloud-ui-compose.yaml ]; then
  ./script/dev/create-config.sh
fi

docker-compose -p firecloud -f target/config/firecloud-ui-compose.yaml up
