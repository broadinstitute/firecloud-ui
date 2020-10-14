#!/bin/bash

# Jira ticket: https://broadworkbench.atlassian.net/browse/BA-6623
# Methods/workflows used in this test:
# https://firecloud.dsde-alpha.broadinstitute.org/#workspaces/cromwell-tests-billing-project/cromwell-tests-workspace/method-configs

set -euo pipefail

VAULT_TOKEN=$(cat /etc/vault-token-dsde)
WORKING_DIR=$PWD

JSON_CREDS=`docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN broadinstitute/dsde-toolbox vault read -format=json secret/dsde/firecloud/dev/common/firecloud-account.json | jq '.data'`

BEARER_TOKEN=$(
        docker \
            run \
            --rm \
            -v "${WORKING_DIR}:/app/populate" \
            -w /app/populate \
            broadinstitute/dsp-toolbox \
            python get_bearer_token.py "dumbledore.admin@test.firecloud.org" "${JSON_CREDS}"
     )

source ./submission-perf-inc.sh

ENV=alpha

# this `true` which goes as a third parameter from the end on the following line is to enable call-caching
launchSubmission dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace 35k-testing wide_scatter "" "" true false ""
findLastSubmissionID dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace
submission1=$submissionID

waitForSubmissionAndWorkflowStatus 20 Done Succeeded dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$submission1"
findFirstWorkflowIdInSubmission dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$submission1"
