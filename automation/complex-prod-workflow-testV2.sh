#!/usr/bin/env bash

# Script to start complex workflow test in Production

set -e
set -x

ENV=$1
VAULT_TOKEN=${2:-$(cat $HOME/.vault-token)}
WORKING_DIR=${3:-$PWD}
NEED_TOKEN=false

# Check if $ENV is not empty
if [[ ! ${ENV} ]]; then
   # echo "ENV is empty. Run again \`sh complex-prod-workflow-test.sh <Production>\`"
    exit 1
else
    echo "Starting complex workflow test V2 in Production"
fi

JSON_CREDS=`docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=https://clotho.broadinstitute.org:8200 broadinstitute/dsde-toolbox vault read -format=json secret/dsde/firecloud/prod/common/canary/firecloud-account.json  | jq '.data'`

users=(
     dumbledore.admin@test.firecloud.org
   )

source ./prod-workflow-inc.sh

# check if user needs a token refresh
    for user in "${users[@]}"
    do
        checkToken $user
    done

    if [ "$NEED_TOKEN" = true ]; then
       exit 1
    fi


if [ $ENV = "prod" ]; then
    SECONDS=0
    launchSubmission \
        dumbledore.admin@test.firecloud.org \
        whitelisted-v2-project \
        complex-featured-workflow_V2 \
        gd-five-dollar-genome \
        five-dollar-genome-analysis-pipeline_copy \
        sample \
        na12878_real_small \
        false \
        false \

   #Monitor the progress of the perf test
    monitorSubmission dumbledore.admin@test.firecloud.org whitelisted-v2-project complex-featured-workflow_V2 $submissionId

   i=1

   while [ "$submissionStatus" != "Done" ] && [ "$i" -le 48 ]

    do
            echo $i
            sleep 5m
            monitorSubmission dumbledore.admin@test.firecloud.org whitelisted-v2-project complex-featured-workflow_V2 $submissionId
            ((i++))
    done

    if [ "$submissionStatus" == "Done" ] && [ "$workflowsStatus" == "Succeeded" ]; then
      timer=$SECONDS
      echo "Complex workflow V2 finished within 4 hours with workflow status: $workflowsStatus"

      echo "[{\"eventType\":\"ComplexWorkflowTestV2_Prod\",\"type\":\"Workflow\",\"status\": \"$workflowsStatus\",\"timeToComplete (sec)\":\"$timer\"}]" > complexWorkflowV2_events.json

      cat complexWorkflowV2_events.json | gzip -c | curl --data-binary @- -X POST -H "Content-Type: application/json" -H "X-Insert-Key: $newRelicKey" -H "Content-Encoding: gzip" https://insights-collector.newrelic.com/v1/accounts/1862859/events

      exit 0
    else
      timer=$SECONDS
      echo "failing with submission status: $submissionStatus and workflow status: $workflowsStatus"

      echo "[{\"eventType\":\"ComplexWorkflowTestV2_Prod\",\"type\":\"Workflow\",\"status\": \"$workflowsStatus\",\"timeToComplete (sec)\":\"$timer\"}]" > complexWorkflowV2_events.json

      cat complexWorkflow_events.json | gzip -c | curl --data-binary @- -X POST -H "Content-Type: application/json" -H "X-Insert-Key: $newRelicKey" -H "Content-Encoding: gzip" https://insights-collector.newrelic.com/v1/accounts/1862859/events
      exit 1
    fi

else
    echo "Could not find ENV"
    exit 1
fi

printf "\nDone"

