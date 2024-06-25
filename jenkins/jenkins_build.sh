#!/bin/bash

set -eux

GCR_SVCACCT_VAULT="secret/dsde/dsp-techops/common/dspci-wb-gcr-service-account.json"
GCR_REPO_PROJ="broad-dsp-gcr-public"

gcloud auth activate-service-account --key-file=${DSP_TECHOPS_SVC_ACCT}

DOCKER_ARGS=(
  "run"
  "--rm"
  "-v ${HOME}/.config/gcloud:/root/.config/gcloud"
  "google/cloud-sdk"
)

SECRET_ACCESS_ACCOUNT=jenkins-firecloud@broad-dsp-techops.iam.gserviceaccount.com
# Expand the array of args and pass them to `docker`
JSON_CREDS=$(docker ${DOCKER_ARGS[*]} /bin/bash -c "gcloud config set account ${SECRET_ACCESS_ACCOUNT} && gcloud secrets versions access latest --project broad-dsde-dev --secret firecloud-sa")

echo ${JSON_CREDS} | jq . > dspci-wb-gcr-service-account.json

./scripts/build.sh compile -d push -g gcr.io/broad-dsp-gcr-public/${PROJECT} -k "dspci-wb-gcr-service-account.json"

# clean up
rm -f dspci-wb-gcr-service-account.json

