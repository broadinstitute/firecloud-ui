#!/bin/bash

set -euo pipefail

main() {
  if [[ -z "${WORKFLOW_ID}" ]]
  then
    echo "Missing required env input: WORKFLOW_ID"
  fi

  if [[ -z "${EXPECTED_STATUS}" ]]
  then
    echo "Missing required env input: EXPECTED_STATUS"
  fi

  if [[ -z "${VAULT_TOKEN}" ]]
  then
    echo "Missing required env input: VAULT_TOKEN"
  fi

  # Update the archive status to be 'Unarchived' (marked as NULL in the DB).
  # This will trigger the carboniting process
  echo "$(date): Updating archive status of ${WORKFLOW_ID} to NULL (ie Unarchived)"
  docker run --rm \
      -e VAULT_TOKEN=$VAULT_TOKEN \
      broadinstitute/dsde-toolbox mysql-connect.sh -p firecloud -a cromwell1 -e alpha \
      "UPDATE WORKFLOW_METADATA_SUMMARY_ENTRY SET METADATA_ARCHIVE_STATUS = NULL WHERE WORKFLOW_EXECUTION_UUID = '${WORKFLOW_ID}';"


  # At 10s per attempt, this allows 600s (ie 10 minutes) for the carboniter to run.
  # Since the carboniter has a max-time-between on 5 minutes, this should be plenty:
  MAX_ATTEMPTS=60
  ATTEMPT=1
  echo "$(date): Waiting for archive status of ${WORKFLOW_ID} to become ${EXPECTED_STATUS}"
  check_carbonite_failed_too_big
}


# Define a function to ensure that the carboniting has failed because it was too big. Then call it:
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
    exit 0
  elif [[ "ATTEMPT" -gt "MAX_ATTEMPTS" ]]; then
    echo "$(date): [ATTEMPT ${ATTEMPT}] Workflow ${WORKFLOW_ID} took too long to re-carbonite..."
    exit 1
  elif grep -q "NULL" <<< "${ARCHIVE_STATUS}"; then
    echo "$(date): [ATTEMPT ${ATTEMPT}] Workflow ${WORKFLOW_ID}'s archive status is still ${ARCHIVE_STATUS}. Will check again in 10s..."
    sleep 10
    ATTEMPT=$(( ATTEMPT + 1 ))
    check_carbonite_failed_too_big
  else
    echo "$(date): [ATTEMPT ${ATTEMPT}] Workflow ${WORKFLOW_ID} finished carboniting (with status '${ARCHIVE_STATUS}') which was not what we expected ('${EXPECTED_STATUS}')"
    exit 1
  fi
}

main "$@"; exit
