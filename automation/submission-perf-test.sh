#!/bin/bash
# Script to start perf test in $ENV, authorize users with NIH

set -e
set -x

ENV=$1
VAULT_TOKEN=${2:-$(cat $HOME/.vault-token)}
WORKING_DIR=${3:-$PWD}
NEED_TOKEN=false

# Check if $ENV is not empty
if [[ ! ${ENV} ]]; then
   echo "ENV is empty. Run again \`sh submission-perf-test.sh <alpha or staging>\`"
    exit 1
else
    echo "Starting Perf test in {$ENV}"
fi

JSON_CREDS=`docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=https://clotho.broadinstitute.org:8200 broadinstitute/dsde-toolbox vault read -format=json secret/dsde/firecloud/dev/common/firecloud-account.pem | jq '.data'`

users=(
     harry.potter@test.firecloud.org
     ron.weasley@test.firecloud.org
     mcgonagall.curator@test.firecloud.org
     draco.malfoy@test.firecloud.org
     hermione.owner@test.firecloud.org
     dumbledore.admin@test.firecloud.org
   )


checkToken () {
    user=$1

    # Verify that user does not need to refresh their token
    if
        curl -f -v --silent -X GET --header "Accept: application/json" --header "Authorization: Bearer $ACCESS_TOKEN" "https://firecloud-orchestration.dsde-$ENV.broadinstitute.org/api/refresh-token-status"  2>&1 | grep '"requiresRefresh": true'
    then
        echo "$1 needs its refresh token refreshed"
        NEED_TOKEN=true

    fi

}

callbackToNIH() {
    user=$1

    echo "
    Launching calback to NIH for:
    user=$1
    "
     ACCESS_TOKEN=`docker run --rm -v $WORKING_DIR:/app/populate -w /app/populate broadinstitute/dsp-toolbox python get_bearer_token.py "${user}" "${JSON_CREDS}"`

      curl -X POST --header "Content-Type: application/json" --header "Accept: application/json" --header "Authorization: Bearer $ACCESS_TOKEN" -d "{\"jwt\":\"eyJhbGciOiJIUzI1NiJ9.ZmlyZWNsb3VkLWRldg.NPXbSpTmAOUvJ1HX85TauAARnlMKfqBsPjumCC7zE7s\"}" "https://firecloud-orchestration.dsde-$ENV.broadinstitute.org/api/nih/callback"
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
        curl -f "https://firecloud-orchestration.dsde-$ENV.broadinstitute.org/api/workspaces/$namespace/$name/submissions" -H "origin: https://firecloud.dsde-$ENV.broadinstitute.org" -H "accept-encoding: gzip, deflate, br" -H "authorization: Bearer $ACCESS_TOKEN" -H "content-type: application/json" --data-binary "{\"methodConfigurationNamespace\":\"$methodConfigurationNamespace\",\"methodConfigurationName\":\"$methodConfigurationName\",\"entityType\":\"$entityType\",\"entityName\":\"$entityName\",\"useCallCache\":$useCallCache}" --compressed
    else
        curl -f "https://firecloud-orchestration.dsde-$ENV.broadinstitute.org/api/workspaces/$namespace/$name/submissions" -H "origin: https://firecloud.dsde-$ENV.broadinstitute.org" -H "accept-encoding: gzip, deflate, br" -H "authorization: Bearer $ACCESS_TOKEN" -H "content-type: application/json" --data-binary "{\"methodConfigurationNamespace\":\"$methodConfigurationNamespace\",\"methodConfigurationName\":\"$methodConfigurationName\",\"entityType\":\"$entityType\",\"entityName\":\"$entityName\",\"useCallCache\":$useCallCache,\"expression\":\"$expression\"}" --compressed
    fi
}

findSubmissionID() {
    user=$1
    namespace=$2
    name=$3

    ACCESS_TOKEN=`docker run --rm -v $WORKING_DIR:/app/populate -w /app/populate broadinstitute/dsp-toolbox python get_bearer_token.py "${user}" "${JSON_CREDS}"`

    submissionID=$(curl -X GET --header 'Accept: application/json' --header "Authorization: Bearer $ACCESS_TOKEN" "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions"| jq -r '.[] | select(.status == ("Submitted")) | .submissionId')
}

monitorSubmission() {
    user=$1
    namespace=$2
    name=$3
    submissionId=$4

    ACCESS_TOKEN=`docker run --rm -v $WORKING_DIR:/app/populate -w /app/populate broadinstitute/dsp-toolbox python get_bearer_token.py "${user}" "${JSON_CREDS}"`

    submissionStatus=$(curl -X GET --header 'Accept: application/json' --header "Authorization: Bearer $ACCESS_TOKEN" "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions/$submissionId" | jq -r '.status')
    workflowsStatus=$(curl -X GET --header 'Accept: application/json' --header "Authorization: Bearer $ACCESS_TOKEN" "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions/$submissionId"  | jq -r '.workflows[] | .status')
}

# check if user needs a token refresh
    for user in "${users[@]}"
    do
        checkToken $user
    done

    if [ "$NEED_TOKEN" = true ]; then
       exit 1
    fi

    # refresh user's NIH status
    for user in "${users[@]}"
    do
        callbackToNIH $user
    done

if [ $ENV = "alpha" ]; then
    launchSubmission harry.potter@test.firecloud.org perf-test-a Perf-test-A-workspace abcd no_sleep1hr_echo_files sample_set sample_set6k true "this.samples"
    findSubmissionID harry.potter@test.firecloud.org perf-test-a Perf-test-A-workspace
    testA=$submissionID
    echo "$testA"
#    ACCESS_TOKEN=`docker run --rm -v $WORKING_DIR:/app/populate -w /app/populate broadinstitute/dsp-toolbox python get_bearer_token.py "{harry.potter@test.firecloud.org}" "${JSON_CREDS}"`
#    submissionA=`curl -X GET --header 'Accept: application/json' --header "Authorization: Bearer $ACCESS_TOKEN" "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/perf-test-a/Perf-test-A-workspace/submissions/$testA" | jq -r '.status'   `
#    echo "$submissionA"
#    workflowStatusA="$workflowsStatus"
#    echo "$workflowStatusA"
#    launchSubmission ron.weasley@test.firecloud.org perf-test-b Perf-Test-B-W abcd no_sleep1hr_echo_files sample_set sample_set6k true "this.samples"
#    testB=`findSubmissionID ron.weasley@test.firecloud.org perf-test-b Perf-Test-B-W`
#    echo "$testB"
#    sleep 1m
#    launchSubmission mcgonagall.curator@test.firecloud.org perf-test-d Perf-Test-D-W_copy abcd no_sleep1hr_echo_files sample_set sample_set6k true "this.samples"
#    testD=`findSubmissionID mcgonagall.curator@test.firecloud.org perf-test-d Perf-Test-D-W_copy`
#    echo "$testD"
#    sleep 2m
#    launchSubmission draco.malfoy@test.firecloud.org perf-test-e Perf-Test_E_W abcd no_sleep1hr_echo_files sample_set sample_set6k true "this.samples"
#    testE=`findSubmissionID draco.malfoy@test.firecloud.org perf-test-e Perf-Test_E_W`
#    echo "$testE"
#    sleep 1m
#    launchSubmission hermione.owner@test.firecloud.org aa-test041417 Perf-Test-G-W abcd no_sleep1hr_echo_files sample_set sample_set6k true "this.samples"
#    testG=`findSubmissionID hermione.owner@test.firecloud.org aa-test041417 Perf-Test-G-W`
#    echo "$testG"
#    launchSubmission dumbledore.admin@test.firecloud.org aa-test-042717a test-042717 anuMethods callCacheWDL participant subject_HCC1143 true

#    #Monitor the progress of the OneOff submission
#    findSubmissionID dumbledore.admin@test.firecloud.org aa-test-042717a test-042717
#    monitorSubmission dumbledore.admin@test.firecloud.org aa-test-042717a test-042717 $submissionId
#
#   i=1
#   while [ "$submissionStatus" != "Done" ] && [ "$i" -le 19 ]
#
#    do
#            echo $i
#            sleep 10m
#            monitorSubmission dumbledore.admin@test.firecloud.org aa-test-042717a test-042717 $submissionId
#            ((i++))
#    done
#
#    if [ "$submissionStatus" == "Done" ] && [ "$workflowsStatus" == "Succeeded" ]; then
#      echo "One-off workflow finished within 3 hours with workflow status: $workflowsStatus"
#      # exit 0
#    else
#      echo "failing with submission status: $submissionStatus and workflow status: $workflowsStatus"
#      exit 1
#    fi
##########################################################################################
#    #Monitor the progress of the rest of submissions
#   i=1
#   [ "$i" -le 30 ]
#   while [ "$submissionA" != "Done" ] # && [ "$submissionB" != "Done" ] && [ "$submissionD" != "Done" ] && [ "$submissionE" != "Done" ] && [ "$submissionG" != "Done" ] && [ "$i" -le 30 ]
#    do
#            echo $i
#            sleep 10m
#
#            submissionA=`monitorSubmission harry.potter@test.firecloud.org perf-test-a Perf-test-A-workspace $testA`
#            echo "$submissionA"
#            workflowStatusA="$workflowsStatus"
#            echo "$workflowStatusA"
#            submissionB= monitorSubmission ron.weasley@test.firecloud.org perf-test-b Perf-Test-B-W $testB
#            workflowStatusB="$workflowsStatus"
#            submissionD= monitorSubmission mcgonagall.curator@test.firecloud.org perf-test-d Perf-Test-D-W_copy $testD
#            workflowStatusD= $workflowsStatus
#            submissionE= monitorSubmission draco.malfoy@test.firecloud.org perf-test-e Perf-Test_E_W $testE
#            workflowStatusE= $workflowsStatus
#            submissionG= monitorSubmission hermione.owner@test.firecloud.org aa-test041417 Perf-Test-G-W $testG
#            workflowStatusG= $workflowsStatus
#            ((i++))
#    done

#    if [ "$submissionA" == "Done" ]
#
#
#    if [ "$submissionB" == "Done" ]
#
#    if [ "$submissionD" == "Done" ]
#
#    if [ "$submissionE" == "Done" ]
#
#    if [ "$submissionG" == "Done" ]


#########################################################################################
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
