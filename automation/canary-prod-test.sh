#!/bin/bash
# Script to start canary test in Production

set -e
set -x

ENV=$1
VAULT_TOKEN=${2:-$(cat $HOME/.vault-token)}
WORKING_DIR=${3:-$PWD}
NEED_TOKEN=false

# Check if $ENV is not empty
if [[ ! ${ENV} ]]; then
   # echo "ENV is empty. Run again \`sh canary-prod-test.sh <Production>\`"
    exit 1
else
    echo "Starting canary test in Production"
fi

JSON_CREDS=`docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=https://clotho.broadinstitute.org:8200 broadinstitute/dsde-toolbox vault read -format=json secret/dsde/firecloud/prod/common/canary/firecloud-account.pem | jq '.data'`

users=(
     dumbledore.admin@test.firecloud.org
   )

checkToken () {
    user=$1

    # Verify that user does not need to refresh their token
    if
        curl -f -v --silent -X GET --header "Accept: application/json" --header "Authorization: Bearer $ACCESS_TOKEN" "https://api.firecloud.org/api/refresh-token-status"  2>&1 | grep '"requiresRefresh": true'
    then
        echo "$1 needs its refresh token refreshed"
        NEED_TOKEN=true
    fi
}

launchSubmission() {
    user=$1
    namespace=$2
    name=$3
    methodConfigurationNamespace=$4
    methodConfigurationName=$5
    entityType=$6
    entityName=$7
    useCallCache=$8
    expression=$9  #optional

    echo "
    Launching submission for:
        user=$1
        namespace=$2
        name=$3
        methodConfigurationNamespace=$4
        methodConfigurationName=$5
        entityType=$6
        entityName=$7
        useCallCache=$8
        expression=$9
    "

     ACCESS_TOKEN=`docker run --rm -v $WORKING_DIR:/app/populate -w /app/populate broadinstitute/dsp-toolbox python get_bearer_token.py "${user}" "${JSON_CREDS}"`

   # check if $9 is set for expression
    if [ -z ${9+x} ] ; then
        submissionId=$(curl -X POST "https://api.firecloud.org/api/workspaces/$namespace/$name/submissions" -H "origin: https://portal.firecloud.org" -H "accept-encoding: gzip, deflate, br" -H "authorization: Bearer $ACCESS_TOKEN" -H "content-type: application/json" --data-binary "{\"methodConfigurationNamespace\":\"$methodConfigurationNamespace\",\"methodConfigurationName\":\"$methodConfigurationName\",\"entityType\":\"$entityType\",\"entityName\":\"$entityName\",\"useCallCache\":$useCallCache}" --compressed | jq -r '.submissionId')
    else
        submissionId=$(curl -X POST "https://api.firecloud.org/api/workspaces/$namespace/$name/submissions" -H "origin: https://portal.firecloud.org" -H "accept-encoding: gzip, deflate, br" -H "authorization: Bearer $ACCESS_TOKEN" -H "content-type: application/json" --data-binary "{\"methodConfigurationNamespace\":\"$methodConfigurationNamespace\",\"methodConfigurationName\":\"$methodConfigurationName\",\"entityType\":\"$entityType\",\"entityName\":\"$entityName\",\"useCallCache\":$useCallCache,\"expression\":\"$expression\"}" --compressed | jq -r '.submissionId')
    fi
    echo $submissionId
}

monitorSubmission() {
    user=$1
    namespace=$2
    name=$3
    submissionId=$4

    ACCESS_TOKEN=`docker run --rm -v $WORKING_DIR:/app/populate -w /app/populate broadinstitute/dsp-toolbox python get_bearer_token.py "${user}" "${JSON_CREDS}"`

    submissionStatus=$(curl -X GET --header 'Accept: application/json' --header "Authorization: Bearer $ACCESS_TOKEN" "https://api.firecloud.org/api/workspaces/$namespace/$name/submissions/$submissionId" | jq -r '.status')
    workflowsStatus=$(curl -X GET --header 'Accept: application/json' --header "Authorization: Bearer $ACCESS_TOKEN" "https://api.firecloud.org/api/workspaces/$namespace/$name/submissions/$submissionId"  | jq -r '.workflows[] | .status')
}

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
    launchSubmission dumbledore.admin@test.firecloud.org broad-firecloud-dsde CanaryTest wdl-testing hello-world participant subject_HCC1143 false

    #Monitor the progress of the perf test
    monitorSubmission dumbledore.admin@test.firecloud.org broad-firecloud-dsde CanaryTest $submissionId

   i=1

   while [ "$submissionStatus" != "Done" ] && [ "$i" -le 20 ]

    do
            echo $i
            sleep 1m
            monitorSubmission dumbledore.admin@test.firecloud.org broad-firecloud-dsde CanaryTest $submissionId
            ((i++))
    done

    if [ "$submissionStatus" == "Done" ] && [ "$workflowsStatus" == "Succeeded" ]; then
      timer=$SECONDS
      echo "One-off workflow finished within 15 minutes with workflow status: $workflowsStatus"

      echo "[{\"eventType\":\"CanaryTestProd\",\"type\":\"Workflow\",\"status\": \"$workflowsStatus\",\"timeToComplete (sec)\":\"$timer\"}]" > canary_events.json

      cat canary_events.json | gzip -c | curl --data-binary @- -X POST -H "Content-Type: application/json" -H "X-Insert-Key: $newRelicKey" -H "Content-Encoding: gzip" https://insights-collector.newrelic.com/v1/accounts/1862859/events

      exit 0
    else
      timer=$SECONDS
      echo "failing with submission status: $submissionStatus and workflow status: $workflowsStatus"

      echo "[{\"eventType\":\"CanaryTestProd\",\"type\":\"Workflow\",\"status\": \"$workflowsStatus\",\"timeToComplete (sec)\":\"$timer\"}]" > canary_events.json
      
      cat canary_events.json | gzip -c | curl --data-binary @- -X POST -H "Content-Type: application/json" -H "X-Insert-Key: $newRelicKey" -H "Content-Encoding: gzip" https://insights-collector.newrelic.com/v1/accounts/1862859/events
      exit 1
    fi

else
    echo "Could not find ENV"
    exit 1
fi

printf "\nDone"
