#!/bin/bash

set -euo pipefail

#VAULT_TOKEN=$(cat /etc/vault-token-dsde)
WORKFLOW_ID="8fcff291-f4aa-401a-bf16-70e7fa76e01d"
EXPECTED_STATUS="TooLargeToArchive"

if [[ -z "${WORKFLOW_ID}" ]]
then
  echo "Missing required env input: WORKFLOW_ID"
fi

if [[ -z "${EXPECTED_STATUS}" ]]
then
  echo "Missing required env input: EXPECTED_STATUS"
fi


# Update the archive status to be 'Unarchived' (marked as NULL in the DB).
# This will trigger the carboniting process
echo "$(date): Updating archive status of ${WORKFLOW_ID} to NULL (ie Unarchived)"
docker run --rm \
    -e VAULT_TOKEN=$VAULT_TOKEN \
    broadinstitute/dsde-toolbox mysql-connect.sh -p firecloud -a cromwell1 -e alpha \
    "UPDATE WORKFLOW_METADATA_SUMMARY_ENTRY SET METADATA_ARCHIVE_STATUS = NULL WHERE WORKFLOW_EXECUTION_UUID = '${WORKFLOW_ID}';"

# Define a function to ensure that the carboniting has failed because it was too big. Then call it:
ATTEMPT=1
MAX_ATTEMPTS=30 # At 10s per attempt, this allows 300s (ie 5 minutes) for the carboniter to run.
echo "$(date): Waiting for archive status of ${WORKFLOW_ID} to become ${EXPECTED_STATUS}"
check_carbonite_failed_too_big () {
  ARCHIVE_STATUS=$(
    docker run --rm \
       -e VAULT_TOKEN=$VAULT_TOKEN \
       broadinstitute/dsde-toolbox mysql-connect.sh -p firecloud -a cromwell1 -e alpha \
       "SELECT METADATA_ARCHIVE_STATUS FROM WORKFLOW_METADATA_SUMMARY_ENTRY WHERE WORKFLOW_EXECUTION_UUID = '${WORKFLOW_ID}';"\
       | tail -n1
  )

  if grep -q "${EXPECTED_STATUS}" <<< "${ARCHIVE_STATUS}"; then
    echo "$(date): [ATTEMPT ${ATTEMPT}] Workflow ${WORKFLOW_ID} finished carboniting with the status we expected (${EXPECTED_STATUS})"
    return 0
  elif [[ "ATTEMPT" -gt "MAX_ATTEMPTS" ]]; then
    echo "$(date): [ATTEMPT ${ATTEMPT}] Workflow ${WORKFLOW_ID} took too long to re-carbonite..."
    exit 1
  elif grep -q "NULL" <<< "${ARCHIVE_STATUS}"; then
    echo "$(date): [ATTEMPT ${ATTEMPT}] Workflow ${WORKFLOW_ID} is still uncarbonited. Will check again in 10s..."
    sleep 10
    ATTEMPT=$(( ATTEMPT + 1 ))
    check_carbonite_failed_too_big
  else
    echo "$(date): [ATTEMPT ${ATTEMPT}] Workflow ${WORKFLOW_ID} finished carboniting (with status '${ARCHIVE_STATUS}') which was not what we expected ('${EXPECTED_STATUS}')"
    exit 1
  fi
}

check_carbonite_failed_too_big
