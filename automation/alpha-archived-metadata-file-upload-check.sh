#!/bin/bash

set -euo pipefail

VAULT_TOKEN=$(cat /etc/vault-token-dsde)

echo "Fetching workflow id whose metadata was archived recently..."

# Get a workflow which completed at least 40 minutes ago and whose metadata is archived
# (archive-delay in alpha is set to 30 minutes. Hence adding 10 more minutes should give Cromwell enough time to
# archive such a workflow)
ARCHIVED_WORKFLOW_RESULT=$(
  docker run --rm \
     -e VAULT_TOKEN=$VAULT_TOKEN \
     broadinstitute/dsde-toolbox mysql-connect.sh -p firecloud -a cromwell1 -e alpha \
     "SELECT WORKFLOW_EXECUTION_UUID, ROOT_WORKFLOW_EXECUTION_UUID FROM cromwell.WORKFLOW_METADATA_SUMMARY_ENTRY WHERE (METADATA_ARCHIVE_STATUS = 'Archived' OR METADATA_ARCHIVE_STATUS = 'ArchivedAndDeleted') AND END_TIMESTAMP > (NOW() - INTERVAL 1 DAY) AND END_TIMESTAMP <= (NOW() - INTERVAL 40 minute) ORDER BY END_TIMESTAMP DESC LIMIT 1;"\
     | tail -n1
)

WORKFLOW_ID=$(echo $ARCHIVED_WORKFLOW_RESULT | cut -d' ' -f1)
ROOT_WORKFLOW_ID=$(echo $ARCHIVED_WORKFLOW_RESULT | cut -d' ' -f2)

if [ -z "$WORKFLOW_ID" ]
then
  echo "Error: No workflow id found! Exiting the script"
  exit 1
else
  echo "Found workflow id: ${WORKFLOW_ID}"
fi

if [[ $ROOT_WORKFLOW_ID == 'NULL' ]]
then
  FILE_LOCATION=${WORKFLOW_ID}/${WORKFLOW_ID}.csv
else
  FILE_LOCATION=${ROOT_WORKFLOW_ID}/${WORKFLOW_ID}.csv
fi

echo "Checking if CSV file exists for workflow ${WORKFLOW_ID} in bucket 'cromwell-carbonited-workflows-alpha'..."

# If file exists, 'gsutil stat' will print its metadata, else it returns 'No URLs matched' and exits with code 1.
gsutil stat gs://cromwell-carbonited-workflows-alpha/${FILE_LOCATION}
