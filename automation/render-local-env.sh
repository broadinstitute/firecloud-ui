#!/usr/bin/env bash

WORKING_DIR=${1-$(pwd)}
VAULT_TOKEN=$2
ENV=$3
LOCAL=false
LOCAL_PARAM=$4
if [ "$LOCAL_PARAM" = "local_ui" ]; then
  LOCAL=true 
fi

# render ctmpls
docker pull broadinstitute/dsde-toolbox:dev
docker run --rm -e VAULT_TOKEN=${VAULT_TOKEN} \
    -e ENVIRONMENT=${ENV} -e ROOT_DIR=${WORKING_DIR} -v ${WORKING_DIR}:/working \
    -e OUT_PATH=/working/src/test/resources -e INPUT_PATH=/working/docker -e LOCAL=$LOCAL \
    broadinstitute/dsde-toolbox:dev render-templates.sh
