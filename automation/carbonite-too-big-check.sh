#!/bin/bash

set -euo pipefail

VAULT_TOKEN=$(cat /etc/vault-token-dsde)
WORKING_DIR=$PWD

DB_PASSWORD=$(docker run --rm \
    -e VAULT_TOKEN=$VAULT_TOKEN \
    broadinstitute/dsde-toolbox vault read \
    -format=json \
    secret/dsde/firecloud/alpha/cromwell/cromwell1/secrets \
    | jq '.data.db_password' \
    | sed -e 's/^"//' -e 's/"$//')

# Update the archive status to be 'Unarchived' (marked as NULL in the DB).
# This will trigger the carboniting process
WORKFLOW_ID="8fcff291-f4aa-401a-bf16-70e7fa76e01d"
docker run --rm \
    -e VAULT_TOKEN=$VAULT_TOKEN \
    broadinstitute/dsde-toolbox mysql-connect.sh -p firecloud -a cromwell1 -e alpha \
    "UPDATE WORKFLOW_METADATA_SUMMARY_ENTRY SET METADATA_ARCHIVE_STATUS = NULL WHERE WORKFLOW_EXECUTION_UUID = '${WORKFLOW_ID}';"

# Define a function to ensure that the carboniting has failed because it was too big. Then call it:
ATTEMPT=1
MAX_ATTEMPTS = 30 # At 10s per attempt, this allows 5 minutes for the carboniter to run.
check_carbonite_failed_too_big () {
  ARCHIVE_STATUS=$(
  docker run --rm \
     -e VAULT_TOKEN=$VAULT_TOKEN \
     broadinstitute/dsde-toolbox mysql-connect.sh -p firecloud -a cromwell1 -e alpha \
     "SELECT METADATA_ARCHIVE_STATUS FROM WORKFLOW_METADATA_SUMMARY_ENTRY WHERE WORKFLOW_EXECUTION_UUID = '${WORKFLOW_ID}';")

  if grep -q "TooLargeToArchive" <<< "${ARCHIVE_STATUS}"; then
    echo "$(date): [ATTEMPT ${ATTEMPT}] Workflow ${WORKFLOW_ID} was considered too big to carbonite (as expected)"
    return 0
  elif [[ "ATTEMPT" -gt "MAX_ATTEMPTS" ]]; then
    echo "$(date): [ATTEMPT ${ATTEMPT}] Workflow ${WORKFLOW_ID} took too long to re-carbonite..."
    exit 1
  elif grep -q "NULL" <<< "${ARCHIVE_STATUS}"; then
    echo "$(date): [ATTEMPT ${ATTEMPT}] Workflow ${WORKFLOW_ID} is still awaiting carbonite..."
    sleep 10
    check_carbonite_failed_too_big
  else
    echo "$(date): [ATTEMPT ${ATTEMPT}] Workflow ${WORKFLOW_ID} finished carboniting but not in a way we expected:"
    echo "${ARCHIVE_STATUS}"
    exit 1
}

check_carbonite_failed_too_big
