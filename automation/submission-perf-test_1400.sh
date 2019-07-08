#!/bin/bash
# Script to start perf test in $ENV, authorize users with NIH

set -e

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

JSON_CREDS=`docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=https://clotho.broadinstitute.org:8200 broadinstitute/dsde-toolbox vault read -format=json secret/dsde/firecloud/dev/common/firecloud-account.json | jq '.data'`

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
 
    # curl -s -X GET --header 'Accept: application/json' --header "Authorization: Bearer $ACCESS_TOKEN" "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/submissions/queueStatus" | jq -r '"\(now),\(.workflowCountsByStatus.Queued),\(.workflowCountsByStatus.Running),\(.workflowCountsByStatus.Submitted)"' | tee -a workflow-progress-$BUILD_NUMBER.csv
    submissionStatus=$(curl -X GET --header 'Accept: application/json' --header "Authorization: Bearer $ACCESS_TOKEN" "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions/$submissionId" | jq -r '.status')
    workflowsStatus=$(curl -X GET --header 'Accept: application/json' --header "Authorization: Bearer $ACCESS_TOKEN" "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions/$submissionId"  | jq -r '.workflows[] | .status')
    workflowFailures=$(curl -X GET --header 'Accept: application/json' --header "Authorization: Bearer $ACCESS_TOKEN" "https://firecloud-orchestration.dsde-alpha.broadinstitute.org/api/workspaces/$namespace/$name/submissions/$submissionId"  | jq -r '[.workflows[] | select(.status == "Failed")] | length')
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
    launchSubmission harry.potter@test.firecloud.org perf-test-a Perf-test-A-workspace abcd no_sleep1hr_echo_files sample_set sample_set1400 true "this.samples"
    findSubmissionID harry.potter@test.firecloud.org perf-test-a Perf-test-A-workspace
    testA=$submissionID
    echo "$testA"
    monitorSubmission harry.potter@test.firecloud.org perf-test-a Perf-test-A-workspace $testA
    submissionA=$submissionStatus
    sleep 2m
    launchSubmission ron.weasley@test.firecloud.org perf-test-b Perf-Test-B-W abcd no_sleep1hr_echo_files sample_set sample_set1400 true "this.samples"
    findSubmissionID ron.weasley@test.firecloud.org perf-test-b Perf-Test-B-W
    testB=$submissionID
    echo "$testB"
    monitorSubmission ron.weasley@test.firecloud.org perf-test-b Perf-Test-B-W $testB
    submissionB=$submissionStatus
    sleep 1m
    launchSubmission mcgonagall.curator@test.firecloud.org perf-test-d Perf-Test-D-W_copy abcd no_sleep1hr_echo_files sample_set sample_set1400 true "this.samples"
    findSubmissionID mcgonagall.curator@test.firecloud.org perf-test-d Perf-Test-D-W_copy
    testD=$submissionID
    echo "$testD"
    monitorSubmission mcgonagall.curator@test.firecloud.org perf-test-d Perf-Test-D-W_copy $testD
    submissionD=$submissionStatus
    sleep 2m
    launchSubmission draco.malfoy@test.firecloud.org perf-test-e Perf-Test_E_W abcd no_sleep1hr_echo_files sample_set sample_set1400 true "this.samples"
    findSubmissionID draco.malfoy@test.firecloud.org perf-test-e Perf-Test_E_W
    testE=$submissionID
    echo "$testE"
    monitorSubmission draco.malfoy@test.firecloud.org perf-test-e Perf-Test_E_W $testE
    submissionE=$submissionStatus
    sleep 1m
    launchSubmission hermione.owner@test.firecloud.org aa-test041417 Perf-Test-G-W abcd no_sleep1hr_echo_files sample_set sample_set1400 true "this.samples"
    findSubmissionID hermione.owner@test.firecloud.org aa-test041417 Perf-Test-G-W
    testG=$submissionID
    echo "$testG"
    monitorSubmission hermione.owner@test.firecloud.org aa-test041417 Perf-Test-G-W $testG
    submissionG=$submissionStatus
    sleep 1m
    launchSubmission dumbledore.admin@test.firecloud.org aa-test-042717a test-042717 anuMethods callCacheWDL participant subject_HCC1143 true
    findSubmissionID dumbledore.admin@test.firecloud.org aa-test-042717a test-042717
    test1=$submissionID
    echo "$test1"

 #Monitor the progress of the OneOff submission
    monitorSubmission dumbledore.admin@test.firecloud.org aa-test-042717a test-042717 $test1

   i=1
   while [ "$submissionStatus" != "Done" ] && [ "$i" -le 199 ]

    do
            echo "Monitoring one-off submission, this is run number: $i"
            sleep 1m
            monitorSubmission dumbledore.admin@test.firecloud.org aa-test-042717a test-042717 $submissionId
            ((i++))
    done

    if [ "$submissionStatus" == "Done" ] && [ "$workflowsStatus" == "Succeeded" ]; then
      echo "One-off workflow finished within 3 hours with workflow status: $workflowsStatus"
      echo "${submissionStatus}" "${workflowsStatus}" > submissionResults.txt 2>&1
    else
      echo "failing with submission status: $submissionStatus and workflow status: $workflowsStatus"
      echo "${submissionStatus}" "${workflowsStatus}" > submissionResults.txt 2>&1
      exit 1
    fi
##########################################################################################
 #Monitor the progress of the rest of submissions

   j=1
   until [[ $submissionA == "Done"   &&  $submissionB == "Done"  &&  $submissionD == "Done"  &&  $submissionE == "Done"  &&  $submissionG == "Done" ]] && [ "$j" -le 150 ]
    do
            echo "Monitoring the other 5 submissions, this is run number: $j"
            sleep 1m

            monitorSubmission harry.potter@test.firecloud.org perf-test-a Perf-test-A-workspace $testA
            submissionA=$submissionStatus
            echo "Submission A status: $submissionA"
            workflowA=$workflowsStatus
            failuresA=$workflowFailures
            echo "Number of failed workflows A: $failuresA"
            monitorSubmission ron.weasley@test.firecloud.org perf-test-b Perf-Test-B-W $testB
            submissionB=$submissionStatus
            echo "Submission B status: $submissionB"
            workflowB=$workflowsStatus
            failuresB=$workflowFailures
            echo "Number of failed workflows B: $failuresB"
            monitorSubmission mcgonagall.curator@test.firecloud.org perf-test-d Perf-Test-D-W_copy $testD
            submissionD=$submissionStatus
            echo "Submission D status: $submissionD"
            workflowD=$workflowsStatus
            failuresD=$workflowFailures
            echo "Number of failed workflows D: $failuresD"
            monitorSubmission draco.malfoy@test.firecloud.org perf-test-e Perf-Test_E_W $testE
            submissionE=$submissionStatus
            echo "Submission E status: $submissionE"
            workflowE=$workflowsStatus
            failuresE=$workflowFailures
            echo "Number of failed workflows E: $failuresE"
            monitorSubmission hermione.owner@test.firecloud.org aa-test041417 Perf-Test-G-W $testG
            submissionG=$submissionStatus
            echo "Submission G status: $submissionG"
            workflowG=$workflowsStatus
            failuresG=$workflowFailures
            echo "Number of failed workflows G: $failuresG"
            ((j++))
    done

    totalFailures=$(( $failuresA+$failuresB+$failuresD+$failuresE+$failuresG ))
    if [ "$totalFailures" -le 30 ]; then
        echo "Nightly Alpha test succeded  with $totalFailures total failed workflows"
        echo "${totalFailures}" &> submissionResults.txt 2>&1
        exit 0
    else
        echo "Nightly Alpha test failed with $totalFailures total failed workflows"
        echo "${totalFailures}" &> submissionResults.txt 2>&1
        exit 1
    fi

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

