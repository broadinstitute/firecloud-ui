#!/bin/bash

set -eux

GCR_SVCACCT_VAULT="secret/dsde/dsp-techops/common/dspci-wb-gcr-service-account.json"
GCR_REPO_PROJ="broad-dsp-gcr-public"
VAULT_TOKEN=${VAULT_TOKEN:-$(cat /etc/vault-token-dsde)}

docker run --rm  -e VAULT_TOKEN=$VAULT_TOKEN \
   broadinstitute/dsde-toolbox:latest vault read --format=json ${GCR_SVCACCT_VAULT} \
   | jq .data > dspci-wb-gcr-service-account.json

./docker/build.sh jar -d push -g gcr.io/broad-dsp-gcr-public/${PROJECT} -k "dspci-wb-gcr-service-account.json"

# clean up
rm -f dspci-wb-gcr-service-account.json

