#!/bin/bash
# Script to start canary test in Production

set -e
set -x

ENV=$1
WORKING_DIR=${3:-$PWD}
NEED_TOKEN=false

# Check if $ENV is not empty
if [[ ! ${ENV} ]]; then
   # echo "ENV is empty. Run again \`sh canary-prod-test.sh <Production>\`"
    exit 1
else
    echo "Starting canary test in Production"
fi

DOCKER_ARGS=(
  "run"
  "--rm"
  "-v ${HOME}/.config/gcloud:/root/.config/gcloud"
  "google/cloud-sdk"
  "gcloud secrets versions access latest --project broad-dsde-dev --secret firecloud-sa"
)

# Expand the array of args and pass them to `docker`
JSON_CREDS=$(docker ${DOCKER_ARGS[*]})

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
        broad-firecloud-dsde \
        CanaryTest \
        wdl-testing \
        hello-world \
        participant \
        subject_HCC1143 \
        false \
        false \

    #Monitor the progress of the perf test
    monitorSubmission dumbledore.admin@test.firecloud.org broad-firecloud-dsde CanaryTest $submissionId

   i=1

   while [ "$submissionStatus" != "Done" ] && [ "$i" -le 60 ]

    do
            echo $i
            sleep 60
            monitorSubmission dumbledore.admin@test.firecloud.org broad-firecloud-dsde CanaryTest $submissionId
            ((i++))
    done

    if [ "$submissionStatus" == "Done" ] && [ "$workflowsStatus" == "Succeeded" ]; then
      timer=$SECONDS
      echo "One-off workflow finished within 60 minutes with workflow status: $workflowsStatus"

      echo "[{\"eventType\":\"CanaryTestProd\",\"type\":\"Workflow\",\"status\": \"$workflowsStatus\",\"timeToComplete (sec)\":\"$timer\"}]" > canary_events.json
      exit 0
    else
      timer=$SECONDS
      echo "failing with submission status: $submissionStatus and workflow status: $workflowsStatus"

      echo "[{\"eventType\":\"CanaryTestProd\",\"type\":\"Workflow\",\"status\": \"$workflowsStatus\",\"timeToComplete (sec)\":\"$timer\"}]" > canary_events.json
      exit 1
    fi

else
    echo "Could not find ENV"
    exit 1
fi

printf "\nDone"
