#!/usr/bin/env bash

WORKING_DIR=${1-$(pwd)}
VAULT_TOKEN=$2
ENV=$3

# if using a set of configs different from the default (i.e. docker/smoketest-configs)
CONFIG_DIR=${4-docker}


# render ctmpls
docker pull broadinstitute/dsde-toolbox:dev
docker run --rm -e VAULT_TOKEN=${VAULT_TOKEN} \
    -e ENVIRONMENT=${ENV} -e ROOT_DIR=${WORKING_DIR} -v ${WORKING_DIR}:/working \
    -e OUT_PATH=/working/src/test/resources -e INPUT_PATH=/working/${CONFIG_DIR} \
    broadinstitute/dsde-toolbox:dev render-templates.sh
