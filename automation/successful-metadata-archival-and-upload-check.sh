#!/bin/bash

set -euo pipefail

VAULT_TOKEN=$(cat /etc/vault-token-dsde)

MOCK_ARCHIVED_RESULT="2ce544a0-4c0d-4cc9-8a0b-b412bb1e5f82 NULL"

echo "------ ACTUAL QUERY, BUT USING WORKFLOW_STATUS ------"

# Get a workflow id which has been archived and END_TIMESTAMP is at least NOW-30 mins?
#ARCHIVED_WORKFLOW_RESULT=$(
#  docker run --rm \
#     -e VAULT_TOKEN=$VAULT_TOKEN \
#     broadinstitute/dsde-toolbox mysql-connect.sh -p firecloud -a cromwell1 -e alpha \
#     "SELECT WORKFLOW_EXECUTION_UUID, ROOT_WORKFLOW_EXECUTION_UUID FROM cromwell.WORKFLOW_METADATA_SUMMARY_ENTRY WHERE METADATA_ARCHIVE_STATUS = 'Archived' AND END_TIMESTAMP <= DATE_SUB(NOW(), INTERVAL 40 minute) ORDER BY END_TIMESTAMP DESC LIMIT 1;"\
#     | tail -n1
#)

ARCHIVED_WORKFLOW_RESULT=$(
  docker run --rm \
     -e VAULT_TOKEN=$VAULT_TOKEN \
     broadinstitute/dsde-toolbox mysql-connect.sh -p firecloud -a cromwell1 -e alpha \
     "SELECT WORKFLOW_EXECUTION_UUID, ROOT_WORKFLOW_EXECUTION_UUID FROM cromwell.WORKFLOW_METADATA_SUMMARY_ENTRY WHERE WORKFLOW_STATUS = 'Succeeded' AND END_TIMESTAMP <= DATE_SUB(NOW(), INTERVAL 40 minute) ORDER BY END_TIMESTAMP DESC LIMIT 1;"\
     | tail -n1
)

WORKFLOW_ID=$(echo $ARCHIVED_WORKFLOW_RESULT | cut -d' ' -f1)
ROOT_WORKFLOW_ID=$(echo $ARCHIVED_WORKFLOW_RESULT | cut -d' ' -f2)

if [[ $ROOT_WORKFLOW_ID == 'NULL' ]]
then
  FILE_LOCATION=${WORKFLOW_ID}/${WORKFLOW_ID}.csv
else
  FILE_LOCATION=${ROOT_WORKFLOW_ID}/${WORKFLOW_ID}.csv
fi

echo "Archived result: ${ARCHIVED_WORKFLOW_RESULT}"
echo "File location: ${FILE_LOCATION}"

#echo "Checking if archived CSV file exists for workflow ${WORKFLOW_ID} ..."

# Check whether .csv file for that workflow exists in cromwell-carbonited-workflows-alpha bucket
#FILE_EXISTS=$(gsutil -q stat gs://cromwell-carbonited-workflows-alpha/${FILE_LOCATION}; echo $?)

echo "----- FILE CHECK ON MOCK FILE -----"

MOCK_WORKFLOW_ID=$(echo $MOCK_ARCHIVED_RESULT | cut -d' ' -f1)
MOCK_ROOT_WORKFLOW_ID=$(echo $MOCK_ARCHIVED_RESULT | cut -d' ' -f2)

if [[ $MOCK_ROOT_WORKFLOW_ID == 'NULL' ]]
then
  MOCK_FILE_LOCATION=${MOCK_WORKFLOW_ID}/${MOCK_WORKFLOW_ID}.json
else
  MOCK_FILE_LOCATION=${MOCK_ROOT_WORKFLOW_ID}/${MOCK_WORKFLOW_ID}.json
fi

echo "Checking if file gs://carbonited-workflow-test-bucket/${MOCK_FILE_LOCATION} exists ...."


MOCK_FILE_EXISTS=$(gsutil -q stat gs://carbonited-workflow-test-bucket/${MOCK_FILE_LOCATION}; echo $?)

if [[ $MOCK_FILE_EXISTS == 0 ]]
then
  echo "Archived file exists for ${MOCK_WORKFLOW_ID}"
  exit 0
else
  echo "Archived file doesn't exist for ${MOCK_WORKFLOW_ID}!!"
  exit 1
fi
