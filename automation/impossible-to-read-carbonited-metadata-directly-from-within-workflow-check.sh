#!/bin/bash

# Jira ticket: https://broadworkbench.atlassian.net/browse/BA-6202
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

expectedErrorText="does not have storage.objects.get access"

launchSubmission dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace cromwell_carboniting_test_methods read_carbonited_metadata_in_task_body "" "" false false ""
findLastSubmissionID dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace
submission1=$submissionID

launchSubmission dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace cromwell_carboniting_test_methods read_carbonited_metadata_in_workflow_body "" "" false false ""
findLastSubmissionID dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace
submission2=$submissionID

launchSubmission dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace cromwell_carboniting_test_methods read_carbonited_metadata_in_workflow_input_block "" "" false false ""
findLastSubmissionID dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace
submission3=$submissionID

launchSubmission dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace cromwell_carboniting_test_methods read_carbonited_metadata_in_workflow_output_block "" "" false false ""
findLastSubmissionID dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace
submission4=$submissionID

waitForSubmissionAndWorkflowStatus 20 Done Failed dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$submission1"
findFirstWorkflowIdInSubmission dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$submission1"
checkIfWorkflowErrorMessageContainsSubstring dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$workflowID" "$expectedErrorText"

waitForSubmissionAndWorkflowStatus 20 Done Failed dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace $submission2
findFirstWorkflowIdInSubmission dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$submission2"
checkIfWorkflowErrorMessageContainsSubstring dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$workflowID" "$expectedErrorText"

waitForSubmissionAndWorkflowStatus 20 Done Failed dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$submission3"
findFirstWorkflowIdInSubmission dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$submission3"
checkIfWorkflowErrorMessageContainsSubstring dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$workflowID" "$expectedErrorText"

waitForSubmissionAndWorkflowStatus 20 Done Failed dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$submission4"
findFirstWorkflowIdInSubmission dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$submission4"
checkIfWorkflowErrorMessageContainsSubstring dumbledore.admin@test.firecloud.org cromwell-tests-billing-project cromwell-tests-workspace "$workflowID" "$expectedErrorText"
