#!/bin/bash
# Simple script to start perf test in $ENV

set -e

ENV=$1
VAULT_TOKEN=${2:-$(cat $HOME/.vault-token)}
WORKING_DIR=${3:-$PWD}

# Check if $ENV is not empty
if [[ ! ${ENV} ]]; then
    echo "ENV is empty. Run again \`sh submission-perf-test.sh <alpha or staging>\`"
    exit 1
else echo "Starting Perf test in $ENV"; fi


JSON_CREDS=`docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=https://clotho.broadinstitute.org:8200 broadinstitute/dsde-toolbox vault read -format=json secret/dsde/firecloud/dev/common/firecloud-account.pem | jq '.data'`

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

    # Verify that user does not need to refresh their token
    if curl -f -v --silent -X GET --header "Accept: application/json" --header "Authorization: Bearer $ACCESS_TOKEN" "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/refresh-token-status"  2>&1 | grep '"requiresRefresh": true'
    then
        echo "This user needs its refresh token refreshed"
        exit 1
    fi

    echo "Note: If there is an error message below (with 'curl: (22)' and '404 Not Found') then that user needs to re-link to NIH."

    #check if $9 is set for 'expression'
    if [ -z ${9+x} ] ; then
        curl -f "https://firecloud-orchestration.dsde-$ENV.broadinstitute.org/api/workspaces/$namespace/$name/submissions" -H "origin: https://firecloud.dsde-$ENV.broadinstitute.org" -H "accept-encoding: gzip, deflate, br" -H "authorization: Bearer $ACCESS_TOKEN" -H "content-type: application/json" --data-binary "{\"methodConfigurationNamespace\":\"$methodConfigurationNamespace\",\"methodConfigurationName\":\"$methodConfigurationName\",\"entityType\":\"$entityType\",\"entityName\":\"$entityName\",\"useCallCache\":$useCallCache}" --compressed
    else
        curl -f "https://firecloud-orchestration.dsde-$ENV.broadinstitute.org/api/workspaces/$namespace/$name/submissions" -H "origin: https://firecloud.dsde-$ENV.broadinstitute.org" -H "accept-encoding: gzip, deflate, br" -H "authorization: Bearer $ACCESS_TOKEN" -H "content-type: application/json" --data-binary "{\"methodConfigurationNamespace\":\"$methodConfigurationNamespace\",\"methodConfigurationName\":\"$methodConfigurationName\",\"entityType\":\"$entityType\",\"entityName\":\"$entityName\",\"useCallCache\":$useCallCache,\"expression\":\"$expression\"}" --compressed
    fi
}


if [ $ENV = "alpha" ]; then
    launchSubmission harry.potter@test.firecloud.org perf-test-a Perf-test-A-workspace qamethods sleep1hr_echo_strings sample_set sample_set6k true "this.samples"
    launchSubmission ron.weasley@test.firecloud.org perf-test-b Perf-Test-B-W alex_methods sleep_echo_strings sample_set sample_set6k true "this.samples"
    launchSubmission mcgonagall.curator@test.firecloud.org perf-test-d Perf-Test-D-W_copy alex_methods sleep_echo_strings sample_set sample_set6k true "this.samples"
    launchSubmission draco.malfoy@test.firecloud.org perf-test-e Perf-Test_E_W qamethods sleep1hr_echo_strings sample_set sample_set6k true "this.samples"
    launchSubmission hermione.owner@test.firecloud.org aa-test041417 Perf-Test-G-W alex_methods sleep_echo_strings sample_set sample_set6k true "this.samples"
    launchSubmission dumbledore.admin@test.firecloud.org aa-test-042717a test-042717 anuMethods callCacheWDL participant subject_HCC1143 true

elif [ $ENV = "staging" ]; then
    launchSubmission harry.potter@test.firecloud.org staging-submission-perf-test-a Perf-test-A-workspace submission-perf-test sleep1hr_echo_strings sample_set sample_set6k true "this.samples"
    launchSubmission ron.weasley@test.firecloud.org staging-submission-perf-test-b Perf-Test-B-W submission-perf-test sleep_20min_echo_strings sample_set sample_set6k true "this.samples"
    launchSubmission mcgonagall.curator@test.firecloud.org staging-submission-perf-test-d Perf-Test-D-W_copy submission-perf-test sleep_20min_echo_strings sample_set sample_set6k true "this.samples"
    launchSubmission draco.malfoy@test.firecloud.org staging-submission-perf-test-e Perf-Test_E_W submission-perf-test sleep1hr_echo_strings sample_set sample_set6k true "this.samples"
    launchSubmission hermione.owner@test.firecloud.org staging-submission-perf-test-g Perf-Test-G-W submission-perf-test sleep_20min_echo_strings sample_set sample_set6k true "this.samples"
    launchSubmission dumbledore.admin@test.firecloud.org staging-submission-perf-test-1 Perf-test-oneoff gatk mutect2-gatk4 pair HCC1143_small true

else
    echo "Could not find ENV"
    exit 1
fi


printf "\nDone"