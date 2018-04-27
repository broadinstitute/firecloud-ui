#!/bin/bash
# Simple script to start perf test in alpha
echo "Starting Perf test in alpha"


VAULT_TOKEN=${1:-$(cat $HOME/.vault-token)}
WORKING_DIR=${2:-$PWD}
ENV=alpha

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

    #check if $9 is set for 'expression'
    if [ -z ${9+x} ] ; then
        curl "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions" -H "origin: https://firecloud.dsde-alpha.broadinstitute.org" -H "accept-encoding: gzip, deflate, br" -H "authorization: Bearer $ACCESS_TOKEN" -H "content-type: application/json" --data-binary "{\"methodConfigurationNamespace\":\"$methodConfigurationNamespace\",\"methodConfigurationName\":\"$methodConfigurationName\",\"entityType\":\"$entityType\",\"entityName\":\"$entityName\",\"useCallCache\":$useCallCache}" --compressed
    else
        curl "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions" -H "origin: https://firecloud.dsde-alpha.broadinstitute.org" -H "accept-encoding: gzip, deflate, br" -H "authorization: Bearer $ACCESS_TOKEN" -H "content-type: application/json" --data-binary "{\"methodConfigurationNamespace\":\"$methodConfigurationNamespace\",\"methodConfigurationName\":\"$methodConfigurationName\",\"entityType\":\"$entityType\",\"entityName\":\"$entityName\",\"useCallCache\":$useCallCache,\"expression\":\"$expression\"}" --compressed
    fi
}

launchSubmission harry.potter@test.firecloud.org perf-test-a Perf-test-A-workspace qamethods sleep1hr_echo_strings sample_set sample_set6k true "this.samples"
launchSubmission ron.weasley@test.firecloud.org perf-test-b Perf-Test-B-W alex_methods sleep_echo_strings sample_set sample_set6k true "this.samples"
launchSubmission mcgonagall.curator@test.firecloud.org perf-test-d Perf-Test-D-W_copy alex_methods sleep_echo_strings sample_set sample_set6k true "this.samples"
launchSubmission draco.malfoy@test.firecloud.org perf-test-e Perf-Test_E_W qamethods sleep1hr_echo_strings sample_set sample_set6k true "this.samples"
launchSubmission hermione.owner@test.firecloud.org aa-test041417 Perf-Test-G-W alex_methods sleep_echo_strings sample_set sample_set6k true "this.samples"
launchSubmission dumbledore.admin@test.firecloud.org aa-test-042717a test-042717 anuMethods callCacheWDL participant subject_HCC1143 true

printf "\nDone"
